package dev.lukebemish.docpatcher.plugin.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Data {
    private static JsonElement splitString(@Nullable String string) {
        if (string == null) {
            return new JsonPrimitive("");
        }
        if (string.contains("\n")) {
            JsonArray array = new JsonArray();
            for (String s : string.split("\n")) {
                array.add(s);
            }
            return array;
        }
        return new JsonPrimitive(string);
    }

    private static String processString(JsonElement element) {
        if (element.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonElement e : element.getAsJsonArray()) {
                builder.append(e.getAsString()).append("\n");
            }
            return builder.substring(0, builder.length() - 1);
        }
        return element.getAsString();
    }

    public static JsonElement serialize(JavadocEntry entry) {
        JsonObject object = new JsonObject();
        if (entry.doc() != null) {
            object.add("doc", splitString(entry.doc()));
        }
        if (entry.tags() != null && !entry.tags().isEmpty()) {
            JsonObject tags = new JsonObject();
            entry.tags().forEach((key, value) -> {
                if (!value.isEmpty()) {
                    JsonArray values = new JsonArray();
                    for (String v : value) {
                        values.add(splitString(v));
                    }
                    tags.add(key, values);
                }
            });
            object.add("tags", tags);
        }
        if (entry.parameters() != null && entry.parameters().length != 0) {
            JsonArray parameters = new JsonArray();
            for (String parameter : entry.parameters()) {
                parameters.add(splitString(parameter));
            }
            object.add("parameters", parameters);
        }
        if (entry.typeParameters() != null && entry.typeParameters().length != 0) {
            JsonArray typeParameters = new JsonArray();
            for (String typeParameter : entry.typeParameters()) {
                typeParameters.add(splitString(typeParameter));
            }
            object.add("typeParameters", typeParameters);
        }
        return object;
    }

    public static JavadocEntry deserializeJavadocEntry(JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        String doc;
        JsonElement docElement = object.get("doc");
        if (docElement == null) {
            doc = null;
        } else {
            doc = processString(docElement);
        }
        Map<String, List<String>> tags = new HashMap<>();
        String[] parameters = null;
        String[] typeParameters = null;
        JsonObject tagsElement = object.getAsJsonObject("tags");
        if (tagsElement != null) {
            tagsElement.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                JsonArray value = entry.getValue().getAsJsonArray();
                List<String> values = new ArrayList<>();
                for (JsonElement e : value.getAsJsonArray()) {
                    values.add(processString(e));
                }
                tags.put(entry.getKey(), values);
            });
        }
        JsonArray parametersElement = object.getAsJsonArray("parameters");
        if (parametersElement != null) {
            parameters = new String[parametersElement.size()];
            for (int i = 0; i < parametersElement.size(); i++) {
                parameters[i] = processString(parametersElement.get(i));
            }
        }
        JsonArray typeParametersElement = object.getAsJsonArray("typeParameters");
        if (typeParametersElement != null) {
            typeParameters = new String[typeParametersElement.size()];
            for (int i = 0; i < typeParametersElement.size(); i++) {
                typeParameters[i] = processString(typeParametersElement.get(i));
            }
        }
        return new JavadocEntry(doc, tags, parameters, typeParameters);
    }

    public static JsonElement serialize(ClassJavadoc javadoc) {
        JsonObject object = new JsonObject();
        if (javadoc.clazz() != null) {
            object.add("clazz", serialize(javadoc.clazz()));
        }
        if (javadoc.methods() != null && !javadoc.methods().isEmpty()) {
            JsonObject methods = new JsonObject();
            javadoc.methods().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> methods.add(entry.getKey(), serialize(entry.getValue())));
            object.add("methods", methods);
        }
        if (javadoc.fields() != null && !javadoc.fields().isEmpty()) {
            JsonObject fields = new JsonObject();
            javadoc.fields().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> fields.add(entry.getKey(), serialize(entry.getValue())));
            object.add("fields", fields);
        }
        if (javadoc.innerClasses() != null && !javadoc.innerClasses().isEmpty()) {
            JsonObject innerClasses = new JsonObject();
            javadoc.innerClasses().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> innerClasses.add(entry.getKey(), serialize(entry.getValue())));
            object.add("innerClasses", innerClasses);
        }
        return object;
    }

    public static ClassJavadoc deserializeClassJavadoc(JsonElement node) {
        JsonObject object = node.getAsJsonObject();
        var clazz = object.getAsJsonObject("clazz");
        var methods = object.getAsJsonObject("methods");
        var fields = object.getAsJsonObject("fields");
        var innerClasses = object.getAsJsonObject("innerClasses");
        Map<String, JavadocEntry> methodsMap;
        Map<String, JavadocEntry> fieldsMap;
        Map<String, ClassJavadoc> innerClassesMap;
        if (methods != null) {
            methodsMap = new HashMap<>();
            methods.entrySet().forEach(entry -> methodsMap.put(entry.getKey(), deserializeJavadocEntry(entry.getValue())));
        } else {
            methodsMap = null;
        }
        if (fields != null) {
            fieldsMap = new HashMap<>();
            fields.entrySet().forEach(entry -> fieldsMap.put(entry.getKey(), deserializeJavadocEntry(entry.getValue())));
        } else {
            fieldsMap = null;
        }
        if (innerClasses != null) {
            innerClassesMap = new HashMap<>();
            innerClasses.entrySet().forEach(entry -> innerClassesMap.put(entry.getKey(), deserializeClassJavadoc(entry.getValue())));
        } else {
            innerClassesMap = null;
        }
        return new ClassJavadoc(
            clazz != null ? deserializeJavadocEntry(clazz) : null,
            methodsMap,
            fieldsMap,
            innerClassesMap
        );
    }
}
