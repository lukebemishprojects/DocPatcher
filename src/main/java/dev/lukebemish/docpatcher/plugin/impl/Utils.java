package dev.lukebemish.docpatcher.plugin.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.ForceFullyQualifiedProcessor;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.stream.Stream;

public final class Utils {
    private Utils() {}

    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    public static String toJson(ClassJavadoc javadoc) {
        return GSON.toJson(Data.serialize(javadoc));
    }

    public static ClassJavadoc fromJson(String json) throws IOException {
        try {
            return Data.deserializeClassJavadoc(GSON.fromJson(json, JsonElement.class));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

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
}
