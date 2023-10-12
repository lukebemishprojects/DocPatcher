package dev.lukebemish.docpatcher.plugin.api;

import com.google.gson.JsonElement;
import dev.lukebemish.docpatcher.plugin.impl.SpoonJavadocVisitor;
import dev.lukebemish.docpatcher.plugin.impl.Utils;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import spoon.Launcher;
import spoon.support.compiler.FileSystemFile;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class MakePatchesTask extends DefaultTask {
    @InputFiles
    public abstract DirectoryProperty getClean();
    @InputFiles
    public abstract DirectoryProperty getModified();
    @Input
    public abstract Property<Integer> getJavaVersion();
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    @Inject
    public MakePatchesTask(Project project) {
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
    public void generatePatches() {
        if (getClean().get().getAsFileTree().isEmpty() || getModified().get().getAsFileTree().isEmpty()) {
            return;
        }

        getProject().delete(getOutputDirectory());

        ClassLoader sourceClassLoader = makeClassLoader();

        var visitor = new SpoonJavadocVisitor.Comparing(false, sourceClassLoader);

        getModified().getAsFileTree().visit(fileVisitDetails -> {
            if (!fileVisitDetails.isDirectory() && fileVisitDetails.getFile().getName().endsWith(".java")) {
                RelativePath relativePath = fileVisitDetails.getRelativePath();
                String className = String.join("/", relativePath.getSegments());
                className = className.substring(0, className.length() - 5);
                try {
                    Launcher mLauncher = makeLauncher(sourceClassLoader);
                    mLauncher.addInputResource(new FileSystemFile(fileVisitDetails.getFile()));
                    var mTypes = Utils.buildModel(mLauncher).getAllTypes().stream().toList();
                    if (mTypes.size() != 1) {
                        throw new RuntimeException("Expected 1 type, found " + mTypes.size());
                    }
                    var modified = mTypes.get(0);
                    Path cleanPath = getClean().get().getAsFile().toPath().resolve(relativePath.getPathString());
                    if (!Files.exists(cleanPath)) {
                        throw new RuntimeException("Clean file does not exist: " + cleanPath);
                    }
                    Launcher cLauncher = makeLauncher(sourceClassLoader);
                    cLauncher.addInputResource(new FileSystemFile(cleanPath.toFile()));
                    var cTypes = Utils.buildModel(cLauncher).getAllTypes().stream().toList();
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
