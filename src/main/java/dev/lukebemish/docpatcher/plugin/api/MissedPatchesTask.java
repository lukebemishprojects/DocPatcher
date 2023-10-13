package dev.lukebemish.docpatcher.plugin.api;

import dev.lukebemish.docpatcher.plugin.impl.SpoonRemainingVisitor;
import dev.lukebemish.docpatcher.plugin.impl.Utils;
import net.neoforged.javadoctor.injector.JavadocProvider;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import spoon.Launcher;
import spoon.support.compiler.VirtualFile;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public abstract class MissedPatchesTask extends DefaultTask {
    @InputDirectory
    public abstract DirectoryProperty getPatches();
    @InputDirectory
    public abstract DirectoryProperty getSource();
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();
    @Input
    public abstract Property<Integer> getJavaVersion();

    @Inject
    public MissedPatchesTask(Project project) {
        var version = project.getExtensions().getByType(JavaPluginExtension.class).getToolchain().getLanguageVersion();
        if (version.isPresent()) {
            this.getJavaVersion().convention(version.get().asInt());
        }
    }

    private Launcher makeLauncher(ClassLoader classLoader) {
        return Utils.makeLauncher(getJavaVersion().get(), classLoader);
    }

    private ClassLoader makeClassLoader() {
        return Utils.makeClassLoader(getClasspath().getFiles().stream().map(File::getPath));
    }

    @TaskAction
    public void missedPatches() {
        if (getSource().get().getAsFileTree().isEmpty() || getPatches().get().getAsFileTree().isEmpty()) {
            return;
        }

        ClassLoader sourceClassLoader = makeClassLoader();

        getProject().delete(getOutputDirectory());
        SpoonRemainingVisitor visitor = new SpoonRemainingVisitor();
        JavadocProvider provider = makeProvider();
        getSource().getAsFileTree().visit(fileVisitDetails -> {
            RelativePath relativePath = fileVisitDetails.getRelativePath();
            String fileName = String.join("/", relativePath.getSegments());
            if (!fileVisitDetails.isDirectory() && fileVisitDetails.getFile().getName().endsWith(".java")) {
                String className = fileName.substring(0, fileName.length() - 5);
                try {
                    String contents = Files.readString(fileVisitDetails.getFile().toPath());
                    Launcher launcher = makeLauncher(sourceClassLoader);
                    launcher.addInputResource(new VirtualFile(contents));
                    var type = Utils.buildModel(launcher).getAllTypes().stream().findAny().orElseThrow();
                    ClassJavadoc javadoc = provider.get(className);
                    ClassJavadoc remainder = javadoc == null ? null : visitor.visit(type, javadoc);
                    if (remainder != null) {
                        var output = getOutputDirectory().get().getAsFile().toPath().resolve(className+ ".docpatcher.json");
                        Files.createDirectories(output.getParent());
                        Files.writeString(output, Utils.toJson(remainder));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private JavadocProvider makeProvider() {
        return className -> {
            className = className.replace('.', '/');
            var path = getPatches().get().getAsFile().toPath().resolve(className + ".docpatcher.json");
            if (Files.exists(path)) {
                try {
                    String contents = Files.readString(path);
                    return Utils.fromJson(contents);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return null;
        };
    }
}
