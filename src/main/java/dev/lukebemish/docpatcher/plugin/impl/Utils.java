package dev.lukebemish.docpatcher.plugin.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.javadoctor.io.gson.GsonJDocIO;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.ForceFullyQualifiedProcessor;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.stream.Stream;

public final class Utils {
    private Utils() {}

    public static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapter(ClassJavadoc.class, GsonJDocIO.JAVADOC_READER)
        .registerTypeAdapter(JavadocEntry.class, GsonJDocIO.ENTRY_READER)
        .setPrettyPrinting()
        .create();

    public static Launcher makeLauncher(int javaVersion, ClassLoader classLoader) {
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setComplianceLevel(javaVersion);
        launcher.getEnvironment().setInputClassLoader(classLoader);
        launcher.addProcessor(new ForceFullyQualifiedProcessor());
        return launcher;
    }

    public static ClassLoader makeClassLoader(Stream<String> classpath) {
        return new URLClassLoader(classpath
            .map(path -> {
                try {
                    return new File(path).toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new IllegalStateException("Invalid classpath entry "+path, e);
                }
            })
            .toArray(URL[]::new), null);
    }

    public static CtModel buildModel(Launcher launcher) {
        launcher.buildModel();
        launcher.process();
        return launcher.getModel();
    }

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
