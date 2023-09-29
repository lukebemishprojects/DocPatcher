package dev.lukebemish.docpatcher.plugin.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.javadoctor.injector.JavadocInjector;
import net.neoforged.javadoctor.injector.JavadocProvider;
import net.neoforged.javadoctor.injector.ast.JClassParser;
import net.neoforged.javadoctor.injector.spoon.SpoonClassParser;
import net.neoforged.javadoctor.io.gson.GsonJDocIO;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;
import spoon.Launcher;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

public abstract class ApplyPatchesTask extends DefaultTask {
    @InputDirectory
    public abstract DirectoryProperty getPatches();
    @InputDirectory
    public abstract DirectoryProperty getSource();
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();
    @Input
    private int javaVersion;

    public void setJavaVersion(int javaVersion) {
        this.javaVersion = javaVersion;
    }

    public int getJavaVersion() {
        if (javaVersion == 0) {
            throw new RuntimeException("Java version not set");
        }
        return javaVersion;
    }

    @Inject
    public ApplyPatchesTask(Project project) {
        var version = project.getExtensions().getByType(JavaPluginExtension.class).getToolchain().getLanguageVersion();
        if (version.isPresent()) {
            this.javaVersion = version.get().asInt();
        }
    }

    private Launcher makeLauncher() {
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setComplianceLevel(getJavaVersion());
        return launcher;
    }

    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapter(ClassJavadoc.class, GsonJDocIO.JAVADOC_READER)
        .registerTypeAdapter(JavadocEntry.class, GsonJDocIO.ENTRY_READER)
        .setPrettyPrinting()
        .create();

    @TaskAction
    public void applyPatches() {
        if (getSource().get().getAsFileTree().isEmpty()) {
            return;
        }
        getProject().delete(getOutputDirectory());
        JavadocInjector injector = createInjector();
        getSource().getAsFileTree().visit(fileVisitDetails -> {
            RelativePath relativePath = fileVisitDetails.getRelativePath();
            String fileName = String.join("/", relativePath.getSegments());
            if (!fileVisitDetails.isDirectory() && fileVisitDetails.getFile().getName().endsWith(".java")) {
                String className = fileName.substring(0, fileName.length() - 5);
                try {
                    String contents = Files.readString(fileVisitDetails.getFile().toPath());
                    var result = injector.injectDocs(className, contents, null);
                    result.getResult().ifPresentOrElse(injectionResult -> {
                        try {
                            var output = getOutputDirectory().get().getAsFile().toPath().resolve(fileName);
                            Files.createDirectories(output.getParent());
                            Files.writeString(output, injectionResult.newSource);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, () -> {
                        throw new RuntimeException("Failed to inject docs for " + className + ": " + String.join(", ", result.getProblems()));
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (!fileVisitDetails.isDirectory()) {
                var outPath = getOutputDirectory().get().getAsFile().toPath().resolve(fileName);
                try {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(fileVisitDetails.getFile().toPath(), Files.newOutputStream(outPath));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @NotNull
    private JavadocInjector createInjector() {
        JClassParser parser = new SpoonClassParser(this::makeLauncher);
        JavadocProvider provider = className -> {
            className = className.replace('.', '/');
            var path = getPatches().get().getAsFile().toPath().resolve(className + ".docpatcher.json");
            if (Files.exists(path)) {
                try {
                    String contents = Files.readString(path);
                    return GSON.fromJson(contents, ClassJavadoc.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return null;
        };
        return new JavadocInjector(parser, provider);
    }
}
