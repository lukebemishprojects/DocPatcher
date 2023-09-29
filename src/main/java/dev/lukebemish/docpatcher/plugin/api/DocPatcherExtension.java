package dev.lukebemish.docpatcher.plugin.api;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
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

    public void diff(@ClosureParams(value = SimpleType.class, options = "dev.lukebemish.docpatcher.DiffSettings")
                     @DelegatesTo(value = DiffSettings.class, strategy = Closure.DELEGATE_FIRST)
                     Closure<?> closure) {
        var settings = new DiffSettings(objectFactory, project);
        closure.setDelegate(settings);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        settings.makeTasks(project);
    }
}
