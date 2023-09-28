package dev.lukebemish.docpatcher.patcher.impl;

import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;

public final class Utils {
    private Utils() {}

    public static boolean isEmpty(ClassJavadoc classJavadoc) {
        if (classJavadoc.clazz() != null && !isEmpty(classJavadoc.clazz())) {
            return false;
        }
        if (classJavadoc.fields() != null) {
            for (var field : classJavadoc.fields().entrySet()) {
                if (!isEmpty(field.getValue())) {
                    return false;
                }
            }
        }
        if (classJavadoc.methods() != null) {
            for (var method : classJavadoc.methods().entrySet()) {
                if (!isEmpty(method.getValue())) {
                    return false;
                }
            }
        }
        if (classJavadoc.innerClasses() != null) {
            for (var innerClass : classJavadoc.innerClasses().entrySet()) {
                if (!isEmpty(innerClass.getValue())) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isEmpty(JavadocEntry entry) {
        return entry == null || (
            (entry.doc() == null || entry.doc().isEmpty()) && (entry.tags() == null || entry.tags().isEmpty())
        );
    }
}
