package dev.lukebemish.docpatcher.impl;

import dev.lukebemish.docpatcher.api.DocPatcherExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

public class DocPatcherPlugin implements Plugin<Project> {
    @Override
    public void apply(@NotNull Project project) {
        project.getExtensions().create("docPatcher", DocPatcherExtension.class, project);
    }
}
