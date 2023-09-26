package dev.lukebemish.docpatcher.api;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

public class DiffSettings {
    private String clean;
    private String modified;
    private Configuration source;
    private final DirectoryProperty cleanProperty;
    private final DirectoryProperty modifiedProperty;
    private final Property<SourceSet> sourceSetProperty;
    private final Project project;

    public DiffSettings(ObjectFactory objectFactory, Project project) {
        this.cleanProperty = objectFactory.directoryProperty();
        this.modifiedProperty = objectFactory.directoryProperty();
        this.sourceSetProperty = objectFactory.property(SourceSet.class);
        this.project = project;
    }

    public String getModified() {
        if (modified == null)
            throw new RuntimeException("Modified source set name not set");
        return modified;
    }

    public void setModified(String modified) {
        this.modified = modified;
        this.modifiedProperty.convention(project.getLayout().getProjectDirectory().dir("src").dir(modified).dir("java"));
    }

    public String getClean() {
        if (clean == null)
            throw new RuntimeException("Modified source set name not set");
        return clean;
    }

    public void setClean(String clean) {
        this.clean = clean;
        this.cleanProperty.convention(project.getLayout().getProjectDirectory().dir("src").dir(clean).dir("java"));
    }

    public Configuration getSource() {
        if (source == null)
            throw new RuntimeException("Source configuration not set");
        return source;
    }

    public void setSource(Configuration source) {
        this.source = source;
    }

    public DirectoryProperty getCleanDirectory() {
        return cleanProperty;
    }
    public DirectoryProperty getModifiedDirectory() {
        return modifiedProperty;
    }
    public Property<SourceSet> getSourceSet() {
        return sourceSetProperty;
    }

    void makeTasks(Project project) {
        var sourceSets = (SourceSetContainer)project.getExtensions().getByName("sourceSets");
        SourceSet sourceSet = getSourceSet().getOrElse(sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME));

        project.getTasks().register(getClean()+"ExtractFromSources", DocsExtractTask.class, task -> {
            task.dependsOn(getSource());
            task.getSources().from(getSource());
            task.getOutputDirectory().set(cleanProperty);
            task.getReadOnly().set(true);
        });
        project.getTasks().register(getModified()+"ExtractFromSources", DocsExtractTask.class, task -> {
            task.dependsOn(getSource());
            task.getSources().from(getSource());
            task.getOutputDirectory().set(modifiedProperty);
        });
        sourceSet.java(src -> {
            src.srcDir(modified);
        });
    }
}
