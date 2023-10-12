package dev.lukebemish.docpatcher.plugin.impl;

import spoon.experimental.CtUnresolvedImport;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JavadocImportProcessor {
    private static final String MAIN = "(?<ownerName>[\\w$.]*)(?:#(?<memberName>[\\w%]+)?(?<descFull>\\((?<desc>[\\w$., \\[\\]]+)?\\))?)?";
    private static final Pattern PATTERN = Pattern.compile("@(?<tag>link|linkplain|see|value)(?<space>\\s+)" + MAIN);
    private static final Pattern MAIN_PATTERN = Pattern.compile("^"+MAIN);

    private static String expandBody(CtElement element, final String owner, final String memberName, final String descFull, String desc) {
        final StringBuilder reference = new StringBuilder();
        final String owningClass = getQualifiedClass(element, owner);
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
                desc = processDesc(element, desc);
            }
            reference.append('(').append(desc).append(')');
        }
        return reference.toString();
    }

    public static String processBlockTag(String tag, CtElement element, String doc) {
        if ("see".equals(tag)) {
            return MAIN_PATTERN.matcher(doc).replaceAll(result -> {
                final StringBuilder reference = new StringBuilder();
                final String owner = result.group(1);
                final String memberName = result.group(2);
                final String descFull = result.group(3);
                String desc = result.group(4);
                reference.append(expandBody(element, owner, memberName, descFull, desc));
                return reference.toString();
            });
        }
        return doc;
    }

    public static String expand(CtElement element, String doc) {
        return PATTERN.matcher(doc).replaceAll(result -> {
            final StringBuilder reference = new StringBuilder()
                .append('@').append(result.group(1)).append(result.group(2));
            final String owner = result.group(3);
            final String memberName = result.group(4);
            final String descFull = result.group(5);
            String desc = result.group(6);
            reference.append(expandBody(element, owner, memberName, descFull, desc));
            return reference.toString();
        });
    }

    private static String getQualifiedClass(CtElement element, String original) {
        if (original == null || original.isBlank()) {
            return null;
        }
        var type = qualifyType(element, original);
        if (type.isPresent()) {
            return type.get().getQualifiedName();
        }
        return original;
    }

    private static String processDesc(CtElement element, String desc) {
        List<String> builder = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < desc.length(); i++) {
            i = parseSimpleName(current, desc, i);
            builder.add(current.toString().trim());
            current = new StringBuilder();
        }
        return builder.stream().map(s -> getQualifiedClass(element, s)).collect(Collectors.joining(", "));
    }

    private static int parseSimpleName(StringBuilder builder, String full, int start) {
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

    /*

    The following methods are modified versions of methods from spoon.javadoc.api.parsing.LinkResolver, distributed under
    the following license:

    Copyright (C) INRIA and contributors

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

     */

    private static Optional<CtType<?>> qualifyType(CtElement context, String name) {
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
            return tryLoadModel(context, name);
        }

        CtType<?> directLookupType = context.getFactory().Type().get(name);
        if (directLookupType != null) {
            return Optional.of(directLookupType);
        }

        return tryLoadModel(context, name)
            .or(() -> tryLoadModel(context, "java.lang." + name));
    }

    private static Optional<CtType<?>> getImportedType(CtElement context, String name, CtCompilationUnit parentUnit) {
        Optional<CtType<?>> referencedImportedType = parentUnit.getImports()
            .stream()
            .filter(it -> it.getImportKind() != CtImportKind.UNRESOLVED)
            .filter(it -> it.getReference().getSimpleName().equals(name))
            .findAny()
            .flatMap(ctImport ->
                ctImport.getReferencedTypes()
                    .stream()
                    .filter(it -> it.getSimpleName().equals(name))
                    .findFirst()
                    .map(CtTypeReference::getTypeDeclaration)
            );

        if (referencedImportedType.isPresent()) {
            return referencedImportedType;
        }

        return parentUnit.getImports()
            .stream()
            .filter(it -> it.getImportKind() == CtImportKind.UNRESOLVED)
            .filter(it -> ((CtUnresolvedImport) it).getUnresolvedReference().endsWith("*"))
            .flatMap(it -> {
                String reference = ((CtUnresolvedImport) it).getUnresolvedReference();
                reference = reference.substring(0, reference.length() - 1);

                return tryLoadModel(context, reference + name).stream();
            })
            .findFirst();
    }

    private static Optional<CtType<?>> tryLoadModel(CtElement context, String name) {
        CtType<?> inModel = context.getFactory().Type().get(name);
        return Optional.ofNullable(inModel);
    }
}
