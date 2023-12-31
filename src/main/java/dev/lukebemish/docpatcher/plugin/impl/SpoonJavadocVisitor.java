package dev.lukebemish.docpatcher.plugin.impl;

import com.google.common.collect.Sets;
import net.neoforged.javadoctor.injector.spoon.JVMSignatureBuilder;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.Nullable;
import spoon.reflect.code.CtJavaDoc;
import spoon.reflect.declaration.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract sealed class SpoonJavadocVisitor {

    private final boolean sanitize;
    private final JavadocImportProcessor javadocImportProcessor;

    public SpoonJavadocVisitor(boolean sanitize, ClassLoader classLoader) {
        this.sanitize = sanitize;
        this.javadocImportProcessor = new JavadocImportProcessor(classLoader);
    }

    protected @Nullable CtJavaDoc getJavadoc(CtElement element) {
        var comments = element.getComments();
        return comments.stream().filter(it -> it instanceof CtJavaDoc).map(it -> ((CtJavaDoc) it)).findFirst().orElse(null);
    }

    public static final class Simple extends SpoonJavadocVisitor {
        public Simple(boolean sanitize, ClassLoader classLoader) {
            super(sanitize, classLoader);
        }

        public ClassJavadoc visit(CtType<?> modified) {
            var javadoc = getJavadoc(modified);
            JavadocEntry classJavadocEntry = null;
            if (javadoc != null) {
                String[] parameters = null;
                if (modified instanceof CtRecord ctRecord) {
                    parameters = ctRecord.getRecordComponents().stream().map(CtRecordComponent::getSimpleName).toArray(String[]::new);
                }

                var typeParameters = modified.getFormalCtTypeParameters().stream().map(CtTypeParameter::getSimpleName).toArray(String[]::new);

                classJavadocEntry = processJavadocs(javadoc, null, parameters, typeParameters, null);
            }

            Map<String, JavadocEntry> methods = new HashMap<>();
            Map<String, JavadocEntry> fields = new HashMap<>();

            Map<String, CtExecutable<?>> modifiedMethods = Stream.concat(modified.getMethods().stream(), (modified instanceof CtClass<?> ctClass) ? ctClass.getConstructors().stream() : Stream.<CtExecutable<?>>of()).collect(Collectors.toMap(exec -> {
                final boolean isCtor = exec instanceof CtConstructor<?>;
                return (isCtor ? "<init>" : exec.getSimpleName()) + JVMSignatureBuilder.getJvmMethodSignature(exec);
            }, Function.identity()));

            Map<String, CtField<?>> modifiedFields = modified.getFields().stream().collect(Collectors.toMap(CtField::getSimpleName, Function.identity()));

            for (var entry : modifiedMethods.entrySet()) {
                var visited = visit(entry.getValue());
                if (visited != null) {
                    methods.put(entry.getKey(), visited);
                }
            }

            for (var entry : modifiedFields.entrySet()) {
                var visited = visit(entry.getValue());
                if (visited != null) {
                    fields.put(entry.getKey(), visited);
                }
            }

            Map<String, CtType<?>> modifiedInnerClasses = modified.getNestedTypes().stream().filter(t -> !t.isAnonymous() && !t.isLocalType()).collect(Collectors.toMap(CtType::getSimpleName, Function.identity()));

            Map<String, ClassJavadoc> innerClasses = new HashMap<>();

            for (var entry : modifiedInnerClasses.entrySet()) {
                var visited = visit(entry.getValue());
                if (visited != null) {
                    innerClasses.put(entry.getKey(), visited);
                }
            }

            if (classJavadocEntry == null && methods.isEmpty() && fields.isEmpty() && innerClasses.isEmpty()) {
                return null;
            }
            return new ClassJavadoc(classJavadocEntry, methods.isEmpty() ? null : methods, fields.isEmpty() ? null : fields, innerClasses.isEmpty() ? null : innerClasses);
        }

        private JavadocEntry visit(CtExecutable<?> modified) {
            var javadoc = getJavadoc(modified);
            JavadocEntry javadocEntry = null;
            if (javadoc != null) {
                String[] parameters = modified.getParameters().stream().map(CtParameter::getSimpleName).toArray(String[]::new);

                String[] typeParameters = null;
                if (modified instanceof CtFormalTypeDeclarer formalTypeDeclarer) {
                    typeParameters = formalTypeDeclarer.getFormalCtTypeParameters().stream().map(CtTypeParameter::getSimpleName).toArray(String[]::new);
                }

                javadocEntry = processJavadocs(javadoc, null, parameters, typeParameters, null);
            }

            return javadocEntry;
        }

        private JavadocEntry visit(CtField<?> modified) {
            var javadoc = getJavadoc(modified);
            JavadocEntry javadocEntry = null;
            if (javadoc != null) {
                javadocEntry = processJavadocs(javadoc, null, null, null, null);
            }

            return javadocEntry;
        }
    }

    public static final class TagWrapper extends SpoonJavadocVisitor {
        private final String tag;

        public TagWrapper(String tag, boolean sanitize, ClassLoader classLoader) {
            super(sanitize, classLoader);
            this.tag = tag;
        }

        public ClassJavadoc visit(CtType<?> clean) {
            var javadoc = getJavadoc(clean);
            JavadocEntry classJavadocEntry = null;
            if (javadoc != null) {
                String content = javadoc.getShortDescription();
                if (!content.equals(javadoc.getLongDescription())) {
                    content = content + '\n' + javadoc.getLongDescription();
                }
                classJavadocEntry = fromContent(content);
            }

            Map<String, JavadocEntry> methods = new HashMap<>();
            Map<String, JavadocEntry> fields = new HashMap<>();

            Map<String, CtExecutable<?>> cleanMethods = Stream.concat(clean.getMethods().stream(), (clean instanceof CtClass<?> ctClass) ? ctClass.getConstructors().stream() : Stream.<CtExecutable<?>>of()).collect(Collectors.toMap(exec -> {
                final boolean isCtor = exec instanceof CtConstructor<?>;
                return (isCtor ? "<init>" : exec.getSimpleName()) + JVMSignatureBuilder.getJvmMethodSignature(exec);
            }, Function.identity()));

            Map<String, CtField<?>> cleanFields = clean.getFields().stream().collect(Collectors.toMap(CtField::getSimpleName, Function.identity()));

            for (var entry : cleanMethods.entrySet()) {
                var visited = visit(entry.getValue());
                if (visited != null) {
                    methods.put(entry.getKey(), visited);
                }
            }

            for (var entry : cleanFields.entrySet()) {
                var visited = visit(entry.getValue());
                if (visited != null) {
                    fields.put(entry.getKey(), visited);
                }
            }

            Map<String, CtType<?>> cleanInnerClasses = clean.getNestedTypes().stream().filter(t -> !t.isAnonymous() && !t.isLocalType()).collect(Collectors.toMap(CtType::getSimpleName, Function.identity()));

            Map<String, ClassJavadoc> innerClasses = new HashMap<>();

            for (var entry : cleanInnerClasses.entrySet()) {
                var visited = visit(entry.getValue());
                if (visited != null) {
                    innerClasses.put(entry.getKey(), visited);
                }
            }

            if (classJavadocEntry == null && methods.isEmpty() && fields.isEmpty() && innerClasses.isEmpty()) {
                return null;
            }
            return new ClassJavadoc(classJavadocEntry, methods.isEmpty() ? null : methods, fields.isEmpty() ? null : fields, innerClasses.isEmpty() ? null : innerClasses);
        }

        public JavadocEntry visit(CtExecutable<?> clean) {
            var javadoc = getJavadoc(clean);
            JavadocEntry javadocEntry = null;
            if (javadoc != null) {
                String content = javadoc.getShortDescription();
                if (!content.equals(javadoc.getLongDescription())) {
                    content = content + '\n' + javadoc.getLongDescription();
                }
                javadocEntry = fromContent(content);
            }

            return javadocEntry;
        }

        public JavadocEntry visit(CtField<?> clean) {
            var javadoc = getJavadoc(clean);
            JavadocEntry javadocEntry = null;
            if (javadoc != null) {
                String content = javadoc.getShortDescription();
                if (!content.equals(javadoc.getLongDescription())) {
                    content = content + '\n' + javadoc.getLongDescription();
                }
                javadocEntry = fromContent(content);
            }

            return javadocEntry;
        }

        public JavadocEntry fromContent(String content) {
            Map<String, List<String>> tags = new HashMap<>();
            tags.put(tag, List.of(sanitize(content)));
            return new JavadocEntry(null, tags, null, null);
        }
    }

    public static final class Comparing extends SpoonJavadocVisitor {
        public Comparing(boolean sanitize, ClassLoader classLoader) {
            super(sanitize, classLoader);
        }

        public ClassJavadoc visit(CtType<?> clean, CtType<?> modified) {
            var javadoc = getJavadoc(modified);
            var originalJavadoc = getJavadoc(clean);
            JavadocEntry classJavadocEntry = null;
            if (javadoc != null) {
                String[] parameters = null;
                if (modified instanceof CtRecord ctRecord) {
                    parameters = ctRecord.getRecordComponents().stream().map(CtRecordComponent::getSimpleName).toArray(String[]::new);
                }

                var typeParameters = modified.getFormalCtTypeParameters().stream().map(CtTypeParameter::getSimpleName).toArray(String[]::new);

                classJavadocEntry = processJavadocs(javadoc, originalJavadoc, parameters, typeParameters, clean);
            }

            Map<String, JavadocEntry> methods = new HashMap<>();
            Map<String, JavadocEntry> fields = new HashMap<>();

            Map<String, CtExecutable<?>> cleanMethods = Stream.concat(clean.getMethods().stream(), (clean instanceof CtClass<?> ctClass) ? ctClass.getConstructors().stream() : Stream.<CtExecutable<?>>of()).collect(Collectors.toMap(exec -> {
                final boolean isCtor = exec instanceof CtConstructor<?>;
                return (isCtor ? "<init>" : exec.getSimpleName()) + JVMSignatureBuilder.getJvmMethodSignature(exec);
            }, Function.identity()));

            Map<String, CtField<?>> cleanFields = clean.getFields().stream().collect(Collectors.toMap(CtField::getSimpleName, Function.identity()));

            Map<String, CtExecutable<?>> modifiedMethods = Stream.concat(modified.getMethods().stream(), (modified instanceof CtClass<?> ctClass) ? ctClass.getConstructors().stream() : Stream.<CtExecutable<?>>of()).collect(Collectors.toMap(exec -> {
                final boolean isCtor = exec instanceof CtConstructor<?>;
                return (isCtor ? "<init>" : exec.getSimpleName()) + JVMSignatureBuilder.getJvmMethodSignature(exec);
            }, Function.identity()));

            Map<String, CtField<?>> modifiedFields = modified.getFields().stream().collect(Collectors.toMap(CtField::getSimpleName, Function.identity()));

            for (String desc : Sets.union(cleanMethods.keySet(), modifiedMethods.keySet())) {
                CtExecutable<?> cleanMethod = cleanMethods.get(desc);
                CtExecutable<?> modifiedMethod = modifiedMethods.get(desc);
                if (cleanMethod == null) {
                    throw new RuntimeException("Clean method is null for " + desc + " in " + clean.getQualifiedName());
                }
                if (modifiedMethod == null) {
                    throw new RuntimeException("Modified method is null for " + desc + " in " + modified.getQualifiedName());
                }
                var visited = visit(cleanMethod, modifiedMethod);
                if (visited != null) {
                    methods.put(desc, visited);
                }
            }

            for (String desc : Sets.union(cleanFields.keySet(), modifiedFields.keySet())) {
                CtField<?> cleanField = cleanFields.get(desc);
                CtField<?> modifiedField = modifiedFields.get(desc);
                if (cleanField == null) {
                    throw new RuntimeException("Clean field is null for " + desc + " in " + clean.getQualifiedName());
                }
                if (modifiedField == null) {
                    throw new RuntimeException("Modified field is null for " + desc + " in " + modified.getQualifiedName());
                }
                var visited = visit(cleanField, modifiedField);
                if (visited != null) {
                    fields.put(desc, visited);
                }
            }

            Map<String, CtType<?>> cleanInnerClasses = clean.getNestedTypes().stream().filter(t -> !t.isAnonymous() && !t.isLocalType()).collect(Collectors.toMap(CtType::getSimpleName, Function.identity()));
            Map<String, CtType<?>> modifiedInnerClasses = modified.getNestedTypes().stream().filter(t -> !t.isAnonymous() && !t.isLocalType()).collect(Collectors.toMap(CtType::getSimpleName, Function.identity()));

            Map<String, ClassJavadoc> innerClasses = new HashMap<>();

            for (String desc : Sets.union(cleanInnerClasses.keySet(), modifiedInnerClasses.keySet())) {
                CtType<?> cleanInnerClass = cleanInnerClasses.get(desc);
                CtType<?> modifiedInnerClass = modifiedInnerClasses.get(desc);
                if (cleanInnerClass == null) {
                    throw new RuntimeException("Clean inner class is null for " + desc + " in " + clean.getQualifiedName());
                }
                if (modifiedInnerClass == null) {
                    throw new RuntimeException("Modified inner class is null for " + desc + " in " + modified.getQualifiedName());
                }
                var visited = visit(cleanInnerClass, modifiedInnerClass);
                if (visited != null) {
                    innerClasses.put(desc, visited);
                }
            }

            if (classJavadocEntry == null && methods.isEmpty() && fields.isEmpty() && innerClasses.isEmpty()) {
                return null;
            }
            return new ClassJavadoc(classJavadocEntry, methods.isEmpty() ? null : methods, fields.isEmpty() ? null : fields, innerClasses.isEmpty() ? null : innerClasses);
        }

        private JavadocEntry visit(CtExecutable<?> clean, CtExecutable<?> modified) {
            var javadoc = getJavadoc(modified);
            var originalJavadoc = getJavadoc(clean);
            JavadocEntry javadocEntry = null;
            if (javadoc != null) {
                String[] parameters = modified.getParameters().stream().map(CtParameter::getSimpleName).toArray(String[]::new);

                String[] typeParameters = null;
                if (modified instanceof CtFormalTypeDeclarer formalTypeDeclarer) {
                    typeParameters = formalTypeDeclarer.getFormalCtTypeParameters().stream().map(CtTypeParameter::getSimpleName).toArray(String[]::new);
                }

                javadocEntry = processJavadocs(javadoc, originalJavadoc, parameters, typeParameters, clean);
            }

            return javadocEntry;
        }

        private JavadocEntry visit(CtField<?> clean, CtField<?> modified) {
            var javadoc = getJavadoc(modified);
            var originalJavadoc = getJavadoc(clean);
            JavadocEntry javadocEntry = null;
            if (javadoc != null) {
                javadocEntry = processJavadocs(javadoc, originalJavadoc, null, null, clean);
            }

            return javadocEntry;
        }
    }

    protected @Nullable JavadocEntry processJavadocs(CtJavaDoc javadoc, @Nullable CtJavaDoc originalJavadoc, String[] parameters, String[] typeParameters, @Nullable CtElement originalParent) {
        CtElement parent = javadoc.getParent();

        var content = javadoc.getLongDescription();
        if (!content.equals(javadoc.getShortDescription())) {
            content = javadoc.getShortDescription() + '\n' + content;
        }
        content = javadocImportProcessor.expand(parent, content, originalParent);
        Map<String, List<String>> tags = new HashMap<>();
        for (var tag : javadoc.getTags()) {
            String tagContent = sanitize(tag.getContent());
            if (tag.getType().hasParam()) {
                tagContent = tag.getParam() + " " + tagContent;
            }
            tagContent = processTag(parent, tagContent, tag.getRealName(), originalParent);
            tags.computeIfAbsent(tag.getRealName(), k -> new ArrayList<>()).add(tagContent);
        }
        String finalContent = sanitize(content);
        if (originalJavadoc != null) {
            var originalContent = originalJavadoc.getLongDescription();
            if (!originalContent.equals(originalJavadoc.getShortDescription())) {
                originalContent = originalJavadoc.getShortDescription() + '\n' + originalContent;
            }
            originalContent = javadocImportProcessor.expand(originalParent, originalContent, originalParent);
            if (content.equals(originalContent)) {
                finalContent = null;
            }
            for (var tag : originalJavadoc.getTags()) {
                String tagContent = tag.getContent();
                if (tag.getType().hasParam()) {
                    tagContent = tag.getParam() + " " + tagContent;
                }
                tagContent = processTag(originalParent, tagContent, tag.getRealName(), originalParent);
                tags.computeIfAbsent(tag.getRealName(), k -> new ArrayList<>()).remove(tagContent);
                if (tags.get(tag.getRealName()).isEmpty()) {
                    tags.remove(tag.getRealName());
                }
            }
        }

        List<String> parametersOut = null;
        if (parameters != null && tags.containsKey("param")) {
            var params = tags.get("param");
            parametersOut = new ArrayList<>();
            for (String parameter : parameters) {
                var optional = params.stream().filter(s -> s.trim().split(" ")[0].equals(parameter)).findFirst();
                if (optional.isPresent()) {
                    String trimmedDoc = optional.get().trim();
                    String doc;
                    if (trimmedDoc.length() <= parameter.length()+1) {
                        doc = "";
                    } else {
                        doc = trimmedDoc.substring(parameter.length()+1).trim();
                    }
                    String trimmed = doc.lines().map(String::trim).collect(Collectors.joining(" "));
                    parametersOut.add(trimmed);
                    params.remove(optional.get());
                } else {
                    parametersOut.add("");
                }
            }
            if (params.isEmpty()) {
                tags.remove("param");
            }
        }

        List<String> typeParametersOut = null;
        if (typeParameters != null && tags.containsKey("param")) {
            var params = tags.get("param");
            typeParametersOut = new ArrayList<>();
            for (String parameter : typeParameters) {
                var optional = params.stream().filter(s -> s.trim().split(" ")[0].equals('<'+parameter+'>')).findFirst();
                if (optional.isPresent()) {
                    String doc = optional.get().trim().substring(parameter.length()+3).trim();
                    String trimmed = doc.lines().map(String::trim).collect(Collectors.joining(" "));
                    typeParametersOut.add(trimmed);
                    params.remove(optional.get());
                } else {
                    typeParametersOut.add("");
                }
            }
            if (params.isEmpty()) {
                tags.remove("param");
            }
        }

        if (finalContent != null || !tags.isEmpty() || (parametersOut != null && parametersOut.stream().anyMatch(s -> !s.isEmpty())) || (typeParametersOut != null && typeParametersOut.stream().anyMatch(s -> !s.isEmpty()))) {
            return new JavadocEntry(finalContent, tags.isEmpty() ? null : tags, parametersOut == null || parametersOut.isEmpty() ? null : parametersOut.toArray(String[]::new), typeParametersOut == null || typeParametersOut.isEmpty() ? null : typeParametersOut.toArray(String[]::new));
        }

        return null;
    }

    protected String sanitize(String string) {
        if (!sanitize) {
            return string;
        }
        return StringEscapeUtils.escapeHtml4(string);
    }

    private String processTag(CtElement parent, String tagContent, String tag, @Nullable CtElement original) {
        tagContent = javadocImportProcessor.processBlockTag(tag, parent, tagContent, original);
        tagContent = tagContent.lines().map(String::trim).collect(Collectors.joining("\n"));
        return tagContent;
    }
}
