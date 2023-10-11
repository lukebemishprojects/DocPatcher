package dev.lukebemish.docpatcher.plugin.api;

import com.google.gson.JsonElement;
import dev.lukebemish.docpatcher.plugin.impl.Utils;
import dev.lukebemish.docpatcher.plugin.impl.SpoonJavadocVisitor;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import spoon.Launcher;
import spoon.support.compiler.FileSystemFile;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class MakePatchesTask extends DefaultTask {
    @InputFiles
    public abstract DirectoryProperty getClean();
    @InputFiles
    public abstract DirectoryProperty getModified();
    @Input
    private int javaVersion;
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

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
    public MakePatchesTask(Project project) {
        var version = project.getExtensions().getByType(JavaPluginExtension.class).getToolchain().getLanguageVersion();
        if (version.isPresent()) {
            this.javaVersion = version.get().asInt();
        }
    }

    private Launcher makeLauncher() {
        return Utils.makeLauncher(getJavaVersion());
    }

    @TaskAction
    public void generatePatches() {
        if (getClean().get().getAsFileTree().isEmpty() || getModified().get().getAsFileTree().isEmpty()) {
            return;
        }

        getProject().delete(getOutputDirectory());

        var visitor = new SpoonJavadocVisitor.Comparing(false);

        getModified().getAsFileTree().visit(fileVisitDetails -> {
            if (!fileVisitDetails.isDirectory() && fileVisitDetails.getFile().getName().endsWith(".java")) {
                RelativePath relativePath = fileVisitDetails.getRelativePath();
                String className = String.join("/", relativePath.getSegments());
                className = className.substring(0, className.length() - 5);
                try {
                    Launcher mLauncher = makeLauncher();
                    mLauncher.addInputResource(new FileSystemFile(fileVisitDetails.getFile()));
                    var mTypes = mLauncher.buildModel().getAllTypes().stream().toList();
                    if (mTypes.size() != 1) {
                        throw new RuntimeException("Expected 1 type, found " + mTypes.size());
                    }
                    var modified = mTypes.get(0);
                    Path cleanPath = getClean().get().getAsFile().toPath().resolve(relativePath.getPathString());
                    if (!Files.exists(cleanPath)) {
                        throw new RuntimeException("Clean file does not exist: " + cleanPath);
                    }
                    Launcher cLauncher = makeLauncher();
                    cLauncher.addInputResource(new FileSystemFile(cleanPath.toFile()));
                    var cTypes = cLauncher.buildModel().getAllTypes().stream().toList();
                    if (cTypes.size() != 1) {
                        throw new RuntimeException("Expected 1 type, found " + cTypes.size());
                    }
                    var clean = cTypes.get(0);

                    ClassJavadoc javadoc = visitor.visit(clean, modified);

                    if (javadoc != null) {
                        Path outputPath = getOutputDirectory().get().getAsFile().toPath().resolve(className + ".docpatcher.json");
                        Files.createDirectories(outputPath.getParent());
                        JsonElement json = Utils.toJson(javadoc);
                        Files.writeString(outputPath, Utils.GSON.toJson(json));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
