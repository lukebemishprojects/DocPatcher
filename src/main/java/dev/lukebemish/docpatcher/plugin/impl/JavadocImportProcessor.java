package dev.lukebemish.docpatcher.plugin.impl;

import org.jetbrains.annotations.Nullable;
import spoon.experimental.CtUnresolvedImport;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JavadocImportProcessor {
    private static final String MAIN = "(?<ownerName>[\\w$.]*)(?:#(?<memberName>[\\w%]+)?(?<descFull>\\((?<desc>[\\w$., \\[\\]]+)?\\))?)?(?:\\s+(?<label>^[}\n\r]+))?";
    private static final Pattern PATTERN = Pattern.compile("@(?<tag>link|linkplain|see|value)(?<space>\\s+)" + MAIN);
    private static final Pattern MAIN_PATTERN = Pattern.compile("^"+MAIN);

    private final ClassLoader classLoader;

    public JavadocImportProcessor(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    private String expandBody(CtElement element, final String owner, final String memberName, final String descFull, String desc, @Nullable CtElement original) {
        final StringBuilder reference = new StringBuilder();
        final String owningClass = getQualifiedClass(element, owner, original);
        final boolean hasDesc = descFull != null && !descFull.isBlank();
        if (owningClass != null) {
            reference.append(owningClass);
        }
        if (memberName != null) {
            reference.append('#');
            reference.append(memberName);
        }
        if (hasDesc) {
            if (desc == null) {
                desc = "";
            } else {
                desc = processDesc(element, desc, original);
            }
            reference.append('(').append(desc).append(')');
        }
        return reference.toString();
    }

    private String combineBody(final String owner, final String memberName, final String descFull, final String desc) {
        final StringBuilder reference = new StringBuilder();
        final boolean hasDesc = descFull != null && !descFull.isBlank();
        if (owner != null) {
            reference.append(owner);
        }
        if (memberName != null) {
            reference.append('#');
            reference.append(memberName);
        }
        if (hasDesc) {
            if (desc == null) {
                reference.append("()");
            } else {
                reference.append('(').append(desc).append(')');
            }
        }
        return reference.toString();
    }

    public String processBlockTag(String tag, CtElement element, String doc, @Nullable CtElement original) {
        if ("see".equals(tag)) {
            return MAIN_PATTERN.matcher(doc).replaceAll(result -> {
                final StringBuilder reference = new StringBuilder();
                final String owner = result.group(1);
                final String memberName = result.group(2);
                final String descFull = result.group(3);
                String desc = result.group(4);
                var fqn = expandBody(element, owner, memberName, descFull, desc, original);
                reference.append(fqn);
                var originalName = combineBody(owner, memberName, descFull, desc);
                if (result.group(5) != null) {
                    reference.append(' ').append(result.group(5));
                } else if (!fqn.equals(originalName)) {
                    reference.append(' ').append(originalName.replace('#','.'));
                }
                return reference.toString();
            });
        }
        return doc;
    }

    public String expand(CtElement element, String doc, @Nullable CtElement original) {
        return PATTERN.matcher(doc).replaceAll(result -> {
            final StringBuilder reference = new StringBuilder()
                .append('@').append(result.group(1)).append(result.group(2));
            final String owner = result.group(3);
            final String memberName = result.group(4);
            final String descFull = result.group(5);
            String desc = result.group(6);
            var fqn = expandBody(element, owner, memberName, descFull, desc, original);
            reference.append(fqn);
            var originalName = combineBody(owner, memberName, descFull, desc);
            if (result.group(7) != null) {
                reference.append(' ').append(result.group(5));
            } else if (!fqn.equals(originalName)) {
                reference.append(' ').append(originalName.replace('#','.'));
            }
            return reference.toString();
        });
    }

    private String getQualifiedClass(CtElement element, String name, @Nullable CtElement original) {
        if (name == null || name.isBlank()) {
            return null;
        }
        var type = qualifyType(element, name);
        if (type.isPresent()) {
            return simplifyName(element, type.get(), original);
        }
        return name;
    }

    private String processDesc(CtElement element, String desc, @Nullable CtElement original) {
        List<String> builder = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < desc.length(); i++) {
            i = parseSimpleName(current, desc, i);
            builder.add(current.toString().trim());
            current = new StringBuilder();
        }
        return builder.stream().map(s -> getQualifiedClass(element, s, original)).collect(Collectors.joining(", "));
    }

    private int parseSimpleName(StringBuilder builder, String full, int start) {
        for (int i = start; i < full.length(); i++) {
            char c = full.charAt(i);
            if (c != ',') {
                builder.append(c);
            } else {
                return i + 1;
            }
        }
        return full.length();
    }

    private String simplifyName(CtElement context, CtType<?> type, @Nullable CtElement original) {
        if (original == null) {
            return type.getQualifiedName();
        }
        if (type.getPackage() != null && type.getPackage().getQualifiedName().equals("java.lang")) {
            return type.getSimpleName();
        }
        CtType<?> contextType = context instanceof CtType ? (CtType<?>) context : context.getParent(CtType.class);
        if (contextType != null && contextType.getPackage() != null && type.getPackage() != null && contextType.getPackage().getQualifiedName().equals(type.getPackage().getQualifiedName())) {
            return type.getSimpleName();
        }
        CtCompilationUnit parentUnit = original.getPosition().getCompilationUnit();
        return parentUnit.getImports()
            .stream()
            .map(it -> {
                if (it.getImportKind() == CtImportKind.UNRESOLVED) {
                    var ref = ((CtUnresolvedImport) it).getUnresolvedReference();
                    if (ref.endsWith("*")) {
                        ref = ref.substring(0, ref.length() - 1);
                    }
                    return ref.equals(type.getQualifiedName()) ? type.getSimpleName() : null;
                } else if (it.getImportKind() == CtImportKind.ALL_TYPES) {
                    var packageName = ((CtPackageReference) it.getReference()).getQualifiedName();
                    return packageName.equals(type.getPackage().getQualifiedName()) ? type.getQualifiedName().substring(packageName.length() + 1) : null;
                } else if (it.getImportKind() == CtImportKind.TYPE) {
                    var typeName = it.getReference().getSimpleName();
                    return typeName.equals(type.getSimpleName()) || type.getSimpleName().startsWith(typeName+".") ? type.getSimpleName() : null;
                }
                return null;
            })
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(type.getQualifiedName());
    }

    /*

    The following methods are modified versions of methods from spoon.javadoc.api.parsing.LinkResolver, distributed under
    the following license:

    Copyright (C) INRIA and contributors

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

     */

    private Optional<CtType<?>> qualifyType(CtElement context, String name) {
        CtType<?> contextType = context instanceof CtType ? (CtType<?>) context : context.getParent(CtType.class);

        if (contextType != null && !name.isBlank()) {
            Optional<CtTypeReference<?>> type = contextType.getReferencedTypes()
                .stream()
                .filter(it -> it.getSimpleName().equals(name) || it.getQualifiedName().equals(name))
                .findAny();
            if (type.isPresent()) {
                return Optional.ofNullable(type.get().getTypeDeclaration());
            }

            CtPackage contextPackage = contextType.getPackage();
            if (contextPackage == null && contextType.getDeclaringType() != null) {
                contextPackage = contextType.getDeclaringType().getPackage();
            }
            if (contextPackage != null) {
                CtType<?> siblingType = contextPackage.getType(name);
                if (siblingType != null) {
                    return Optional.of(siblingType);
                }
            }
        }
        if (contextType != null && name.isBlank()) {
            return Optional.of(contextType);
        }

        CtCompilationUnit parentUnit = context.getPosition().getCompilationUnit();
        Optional<CtType<?>> importedType = getImportedType(context, name, parentUnit);
        if (importedType.isPresent()) {
            return importedType;
        }

        // The classes are not imported and not referenced if they are only used in javadoc...
        if (name.startsWith("java.lang")) {
            return tryLoadModelOrReflection(context, name);
        }

        CtType<?> directLookupType = context.getFactory().Type().get(name);
        if (directLookupType != null) {
            return Optional.of(directLookupType);
        }

        return tryLoadModelOrReflection(context, name)
            .or(() -> tryLoadModelOrReflection(context, "java.lang." + name));
    }

    private Optional<CtType<?>> getImportedType(CtElement context, String name, CtCompilationUnit parentUnit) {
        Optional<CtType<?>> referencedImportedType = parentUnit.getImports()
            .stream()
            .filter(it -> it.getImportKind() == CtImportKind.TYPE)
            .filter(it -> it.getReference().getSimpleName().equals(name) || name.startsWith(it.getReference().getSimpleName()+"."))
            .findAny()
            .flatMap(ctImport ->
                ctImport.getReferencedTypes()
                    .stream()
                    .filter(it -> it.getSimpleName().equals(name))
                    .findFirst()
                    .map(CtTypeReference::getTypeDeclaration)
                    .flatMap(type -> {
                        if (!name.equals(ctImport.getReference().getSimpleName())) {
                            String remaining = name.substring(ctImport.getReference().getSimpleName().length() + 1);
                            String[] parts = remaining.split("\\.");
                            CtType<?> current = type;
                            for (String part : parts) {
                                current = current.getNestedType(part);
                                if (current == null) {
                                    return Optional.empty();
                                }
                            }
                            return Optional.of(current);
                        }
                        return Optional.of(type);
                    })
            );

        if (referencedImportedType.isPresent()) {
            return referencedImportedType;
        }

        referencedImportedType = parentUnit.getImports().stream()
            .filter(it -> it.getImportKind() == CtImportKind.ALL_TYPES)
            .filter(it -> it.getReference() instanceof CtPackageReference)
            .flatMap(it -> {
                String reference = ((CtPackageReference) it.getReference()).getQualifiedName();
                return tryLoadModelOrReflection(context, reference + "." + name).stream();
            }).findFirst();

        if (referencedImportedType.isPresent()) {
            return referencedImportedType;
        }

        referencedImportedType = parentUnit.getImports()
            .stream()
            .filter(it -> it.getImportKind() == CtImportKind.UNRESOLVED)
            .filter(it -> ((CtUnresolvedImport) it).getUnresolvedReference().endsWith("*"))
            .flatMap(it -> {
                String reference = ((CtUnresolvedImport) it).getUnresolvedReference();
                reference = reference.substring(0, reference.length() - 1);

                return tryLoadModelOrReflection(context, reference + name).stream();
            })
            .findFirst();

        return referencedImportedType;
    }

    private Optional<CtType<?>> tryLoadModelOrReflection(CtElement context, String name) {
        CtType<?> inModel = context.getFactory().Type().get(name);
        if (inModel != null) {
            return Optional.of(inModel);
        }
        return tryLoadClass(name).map(context.getFactory().Type()::get);
    }

    private Optional<Class<?>> tryLoadClass(String name) {
        try {
            return Optional.of(Class.forName(name, false, classLoader));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }
}
