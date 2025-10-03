package dev.lukebemish.docpatcher.plugin.api;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

public abstract class DocPatcherExtension {
    private final Project project;
    private final ObjectFactory objectFactory;
    @Inject
    public DocPatcherExtension(ObjectFactory objectFactory, Project project) {
        this.project = project;
        this.objectFactory = objectFactory;
    }

    public void diff(Action<DiffSettings> action) {
        var settings = new DiffSettings(objectFactory, project);
        action.execute(settings);
        settings.makeTasks(project);
    }
}
