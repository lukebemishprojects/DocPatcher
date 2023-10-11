package dev.lukebemish.docpatcher.plugin.impl;

import com.google.common.collect.Sets;
import net.neoforged.javadoctor.injector.spoon.JVMSignatureBuilder;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;
import org.jetbrains.annotations.NotNull;
import spoon.reflect.code.CtJavaDoc;
import spoon.reflect.declaration.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SpoonJavadocVisitor {
    public ClassJavadoc visit(CtType<?> clean, CtType<?> modified) {
        var comments = modified.getComments();
        var originalComments = clean.getComments();
        CtJavaDoc originalJavadoc = null;
        if (!originalComments.isEmpty()) {
            var javadocComment = originalComments.get(0);
            if (javadocComment instanceof CtJavaDoc javadoc) {
                originalJavadoc = javadoc;
            }
        }
        JavadocEntry classJavadocEntry = null;
        if (!comments.isEmpty()) {
            var javadocComment = comments.get(0);
            if (javadocComment instanceof CtJavaDoc javadoc) {
                String[] parameters = null;
                if (modified instanceof CtRecord ctRecord) {
                    parameters = ctRecord.getRecordComponents().stream().map(CtRecordComponent::getSimpleName).toArray(String[]::new);
                }

                var typeParameters = modified.getFormalCtTypeParameters().stream().map(CtTypeParameter::getSimpleName).toArray(String[]::new);

                ProcessedJavadoc result = processJavadocs(javadoc, originalJavadoc, parameters, typeParameters);

                if (result.contentDiff() != null || !result.tags().isEmpty()) {
                    classJavadocEntry = new JavadocEntry(result.contentDiff(), result.tags().isEmpty() ? null : result.tags(), result.parameters, result.typeParameters);
                }
            }
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
        var comments = modified.getComments();
        var originalComments = clean.getComments();
        CtJavaDoc originalJavadoc = null;
        if (!originalComments.isEmpty()) {
            var javadocComment = originalComments.get(0);
            if (javadocComment instanceof CtJavaDoc javadoc) {
                originalJavadoc = javadoc;
            }
        }
        JavadocEntry javadocEntry = null;
        if (!comments.isEmpty()) {
            var javadocComment = comments.get(0);
            if (javadocComment instanceof CtJavaDoc javadoc) {
                String[] parameters = modified.getParameters().stream().map(CtParameter::getSimpleName).toArray(String[]::new);

                String[] typeParameters = null;
                if (modified instanceof CtFormalTypeDeclarer formalTypeDeclarer) {
                    typeParameters = formalTypeDeclarer.getFormalCtTypeParameters().stream().map(CtTypeParameter::getSimpleName).toArray(String[]::new);
                }

                ProcessedJavadoc result = processJavadocs(javadoc, originalJavadoc, parameters, typeParameters);

                if (result.contentDiff() != null || !result.tags().isEmpty()) {
                    javadocEntry = new JavadocEntry(result.contentDiff(), result.tags().isEmpty() ? null : result.tags(), result.parameters, result.typeParameters);
                }
            }
        }

        return javadocEntry;
    }

    private JavadocEntry visit(CtField<?> clean, CtField<?> modified) {
        var comments = modified.getComments();
        var originalComments = clean.getComments();
        CtJavaDoc originalJavadoc = null;
        if (!originalComments.isEmpty()) {
            var javadocComment = originalComments.get(0);
            if (javadocComment instanceof CtJavaDoc javadoc) {
                originalJavadoc = javadoc;
            }
        }
        JavadocEntry javadocEntry = null;
        if (!comments.isEmpty()) {
            var javadocComment = comments.get(0);
            if (javadocComment instanceof CtJavaDoc javadoc) {
                ProcessedJavadoc result = processJavadocs(javadoc, originalJavadoc, null, null);

                if (result.contentDiff() != null || !result.tags().isEmpty()) {
                    javadocEntry = new JavadocEntry(result.contentDiff(), result.tags().isEmpty() ? null : result.tags(), null, null);
                }
            }
        }

        return javadocEntry;
    }

    @NotNull
    private static ProcessedJavadoc processJavadocs(CtJavaDoc javadoc, CtJavaDoc originalJavadoc, String[] parameters, String[] typeParameters) {
        var content = javadoc.getLongDescription();
        Map<String, List<String>> tags = new HashMap<>();
        for (var tag : javadoc.getTags()) {
            String tagContent = tag.getContent();
            if (tag.getType().hasParam()) {
                tagContent = tag.getParam() + " " + tagContent;
            }
            tags.computeIfAbsent(tag.getType().getName(), k -> new ArrayList<>()).add(tagContent);
        }
        List<String> parametersOut = null;
        if (parameters != null && tags.containsKey("param")) {
            var params = tags.get("param");
            parametersOut = new ArrayList<>();
            for (String parameter : parameters) {
                var optional = params.stream().filter(s -> s.trim().split(" ")[0].equals(parameter)).findFirst();
                if (optional.isPresent()) {
                    parametersOut.add(optional.get().trim().substring(parameter.length()+1));
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
                    typeParametersOut.add(optional.get().trim().substring(parameter.length()+3));
                    params.remove(optional.get());
                } else {
                    typeParametersOut.add("");
                }
            }
            if (params.isEmpty()) {
                tags.remove("param");
            }
        }
        String contentDiff = content;
        if (originalJavadoc != null) {
            var originalContent = originalJavadoc.getLongDescription();
            if (content.equals(originalContent)) {
                contentDiff = null;
            } else if (content.startsWith(originalContent)) {
                contentDiff = content.substring(originalContent.length());
            }
            for (var tag : originalJavadoc.getTags()) {
                String tagContent = tag.getContent();
                if (tag.getType().hasParam()) {
                    tagContent = tag.getParam() + " " + tagContent;
                }
                tags.computeIfAbsent(tag.getType().getName(), k -> new ArrayList<>()).remove(tagContent);
                if (tags.get(tag.getType().getName()).isEmpty()) {
                    tags.remove(tag.getType().getName());
                }
            }
        }
        return new ProcessedJavadoc(tags, contentDiff, parametersOut == null || parametersOut.isEmpty() ? null : parametersOut.toArray(String[]::new), typeParametersOut == null || typeParametersOut.isEmpty() ? null : typeParametersOut.toArray(String[]::new));
    }

    private record ProcessedJavadoc(Map<String, List<String>> tags, String contentDiff, String[] parameters, String[] typeParameters) {}
}
