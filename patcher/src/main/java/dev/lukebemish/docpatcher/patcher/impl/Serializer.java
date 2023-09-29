package dev.lukebemish.docpatcher.patcher.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.javadoctor.io.gson.GsonJDocIO;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;

public final class Serializer {
    private Serializer() {}

    public static JsonElement toJson(ClassJavadoc classJavadoc) {
        JsonObject json = new JsonObject();
        if (classJavadoc.clazz() != null) {
            json.add("clazz", toJson(classJavadoc.clazz()));
        }
        if (classJavadoc.innerClasses() != null && !classJavadoc.innerClasses().isEmpty()) {
            JsonObject innerClasses = new JsonObject();
            for (var entry : classJavadoc.innerClasses().entrySet()) {
                innerClasses.add(entry.getKey(), toJson(entry.getValue()));
            }
            json.add("innerClasses", innerClasses);
        }
        if (classJavadoc.methods() != null && !classJavadoc.methods().isEmpty()) {
            JsonObject methods = new JsonObject();
            for (var entry : classJavadoc.methods().entrySet()) {
                methods.add(entry.getKey(), toJson(entry.getValue()));
            }
            json.add("methods", methods);
        }
        if (classJavadoc.fields() != null && !classJavadoc.fields().isEmpty()) {
            JsonObject fields = new JsonObject();
            for (var entry : classJavadoc.fields().entrySet()) {
                fields.add(entry.getKey(), toJson(entry.getValue()));
            }
            json.add("fields", fields);
        }
        return json;
    }

    public static JsonElement toJson(JavadocEntry javadocEntry) {
        return GsonJDocIO.GSON.toJsonTree(javadocEntry);
    }
}
