package dev.lukebemish.docpatcher.plugin.api;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

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

    @TaskAction
    public void extract() {
        for (var source : getSources()) {
            getProject().delete(getOutputDirectory());
            getProject().copy(spec -> {
                spec.from(getProject().zipTree(source));
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
