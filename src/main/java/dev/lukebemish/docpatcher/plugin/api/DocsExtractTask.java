package dev.lukebemish.docpatcher.plugin.api;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public abstract class DocsExtractTask extends DefaultTask {
    @InputFiles
    public abstract ConfigurableFileCollection getSources();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<Boolean> getReadOnly();

    public DocsExtractTask() {
        getReadOnly().convention(false);
    }

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Inject
    protected abstract ArchiveOperations getArchiveOperations();

    @TaskAction
    public void extract() {
        for (var source : getSources()) {
            getFileSystemOperations().delete(s -> s.delete(getOutputDirectory()));
            getFileSystemOperations().copy(spec -> {
                spec.from(getArchiveOperations().zipTree(source));
                spec.into(getOutputDirectory());
                if (getReadOnly().get()) {
                    spec.eachFile(fileCopyDetails -> {
                        fileCopyDetails.permissions(permissions -> {
                            permissions.getUser().setWrite(false);
                            permissions.getGroup().setWrite(false);
                            permissions.getOther().setWrite(false);
                        });
                    });
                }
            });
        }
    }
}
