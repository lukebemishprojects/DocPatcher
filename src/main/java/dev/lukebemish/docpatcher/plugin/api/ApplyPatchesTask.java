package dev.lukebemish.docpatcher.plugin.api;

import dev.lukebemish.docpatcher.plugin.impl.JavadocStrippingVisitor;
import dev.lukebemish.docpatcher.plugin.impl.SpoonJavadocVisitor;
import dev.lukebemish.docpatcher.plugin.impl.Utils;
import net.neoforged.javadoctor.injector.CombiningJavadocProvider;
import net.neoforged.javadoctor.injector.JavadocInjector;
import net.neoforged.javadoctor.injector.JavadocProvider;
import net.neoforged.javadoctor.injector.ast.JClassParser;
import net.neoforged.javadoctor.injector.spoon.SpoonClassParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.jetbrains.annotations.NotNull;
import spoon.Launcher;
import spoon.support.compiler.FileSystemFile;
import spoon.support.compiler.VirtualFile;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public abstract class ApplyPatchesTask extends DefaultTask {
    @InputDirectory
    @Optional
    @IgnoreEmptyDirectories
    public abstract DirectoryProperty getPatches();
    @InputDirectory
    public abstract DirectoryProperty getSource();
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();
    @Input
    public abstract Property<Integer> getJavaVersion();
    @Input
    @Optional
    public abstract Property<String> getOriginalTag();
    @Input
    public abstract Property<Boolean> getKeepOriginal();
    @Input
    public abstract Property<Boolean> getSanitizeOriginal();
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    @Inject
    public ApplyPatchesTask(Project project) {
        var version = project.getExtensions().getByType(JavaPluginExtension.class).getToolchain().getLanguageVersion();
        if (version.isPresent()) {
            this.getJavaVersion().convention(version.get().asInt());
        }
        getKeepOriginal().convention(true);
        getSanitizeOriginal().convention(false);
    }

    private Launcher makeLauncher(ClassLoader classLoader) {
        return Utils.makeLauncher(getJavaVersion().get(), classLoader);
    }

    private ClassLoader makeClassLoader() {
        return Utils.makeClassLoader(getClasspath().getFiles().stream().map(File::getPath));
    }

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void applyPatches() {
        if (getSource().get().getAsFileTree().isEmpty()) {
            return;
        }

        ClassLoader sourceClassLoader = makeClassLoader();

        getFileSystemOperations().delete(s -> s.delete(getOutputDirectory()));
        JavadocInjector injector = createInjector(sourceClassLoader);
        getSource().getAsFileTree().visit(fileVisitDetails -> {
            RelativePath relativePath = fileVisitDetails.getRelativePath();
            String fileName = String.join("/", relativePath.getSegments());
            if (!fileVisitDetails.isDirectory() && fileVisitDetails.getFile().getName().endsWith(".java")) {
                String className = fileName.substring(0, fileName.length() - 5);
                try {
                    String contents = Files.readString(fileVisitDetails.getFile().toPath());
                    var launcher = makeLauncher(sourceClassLoader);
                    launcher.addInputResource(new VirtualFile(contents));
                    var visitor = new JavadocStrippingVisitor(contents);
                    for (var type : Utils.buildModel(launcher).getAllTypes()) {
                        visitor.visit(type);
                    }
                    contents = visitor.build();
                    var result = injector.injectDocs(className, className, contents, null);
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
    private JavadocProvider createPatchInjector() {
        if (getPatches().getOrNull() == null) {
            return className -> null;
        }
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

    @NotNull
    private JavadocProvider createOriginalInjector(ClassLoader classLoader) {
        if (!getKeepOriginal().get()) {
            return className -> null;
        }
        return className -> {
            className = className.replace('.', '/');
            var path = getSource().get().getAsFile().toPath().resolve(className + ".java");
            if (Files.exists(path)) {
                String tag = getOriginalTag().getOrNull();
                Launcher launcher = makeLauncher(classLoader);
                launcher.addInputResource(new FileSystemFile(path.toFile()));
                var mTypes = Utils.buildModel(launcher).getAllTypes().stream().toList();
                if (mTypes.size() != 1) {
                    throw new RuntimeException("Expected 1 type, found " + mTypes.size());
                }
                var type = mTypes.get(0);
                if (tag != null) {
                    SpoonJavadocVisitor.TagWrapper visitor = new SpoonJavadocVisitor.TagWrapper(tag, getSanitizeOriginal().get(), classLoader);
                    return visitor.visit(type);
                }
                var visitor = new SpoonJavadocVisitor.Simple(getSanitizeOriginal().get(), classLoader);
                return visitor.visit(type);
            }
            return null;
        };
    }

    @NotNull
    private JavadocInjector createInjector(ClassLoader classLoader) {
        JClassParser parser = new SpoonClassParser(() -> this.makeLauncher(classLoader));
        var patches = createPatchInjector();
        var original = createOriginalInjector(classLoader);
        return new JavadocInjector(parser, new CombiningJavadocProvider(List.of(patches, original)));
    }
}
