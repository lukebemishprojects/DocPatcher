package dev.lukebemish.docpatcher.plugin.api;

import org.apache.commons.lang3.StringUtils;
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
    private String patches;
    private String output;
    private Configuration source;
    private final DirectoryProperty cleanProperty;
    private final DirectoryProperty modifiedProperty;
    private final DirectoryProperty patchesProperty;
    private final DirectoryProperty outputProperty;
    private final Property<SourceSet> cleanSourceSetProperty;
    private final Property<SourceSet> modifiedSourceSetProperty;
    private final Property<SourceSet> patchesSourceSetProperty;
    private final Property<SourceSet> outputSourceSetProperty;
    private final Project project;

    public DiffSettings(ObjectFactory objectFactory, Project project) {
        this.cleanProperty = objectFactory.directoryProperty();
        this.modifiedProperty = objectFactory.directoryProperty();
        this.patchesProperty = objectFactory.directoryProperty();
        this.outputProperty = objectFactory.directoryProperty();
        this.cleanSourceSetProperty = objectFactory.property(SourceSet.class);
        this.modifiedSourceSetProperty = objectFactory.property(SourceSet.class);
        this.patchesSourceSetProperty = objectFactory.property(SourceSet.class);
        this.outputSourceSetProperty = objectFactory.property(SourceSet.class);
        this.project = project;
    }

    public String getOutput() {
        if (output == null)
            throw new RuntimeException("Output name not set");
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
        this.outputProperty.convention(project.getLayout().getProjectDirectory().dir("src").dir(output).dir("java"));
    }

    public String getPatches() {
        if (patches == null)
            throw new RuntimeException("Patches name not set");
        return patches;
    }

    public void setPatches(String patches) {
        this.patches = patches;
        this.patchesProperty.convention(project.getLayout().getProjectDirectory().dir("src").dir(patches).dir("resources"));
    }

    public String getModified() {
        if (modified == null)
            throw new RuntimeException("Modified name not set");
        return modified;
    }

    public void setModified(String modified) {
        this.modified = modified;
        this.modifiedProperty.convention(project.getLayout().getProjectDirectory().dir("src").dir(modified).dir("java"));
    }

    public String getClean() {
        if (clean == null)
            throw new RuntimeException("Clean name not set");
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
    public DirectoryProperty getPatchesDirectory() {
        return patchesProperty;
    }
    public DirectoryProperty getOutputDirectory() {
        return outputProperty;
    }
    public Property<SourceSet> getCleanSourceSet() {
        return cleanSourceSetProperty;
    }
    public Property<SourceSet> getModifiedSourceSet() {
        return modifiedSourceSetProperty;
    }
    public Property<SourceSet> getPatchesSourceSet() {
        return patchesSourceSetProperty;
    }
    public Property<SourceSet> getOutputSourceSet() {
        return outputSourceSetProperty;
    }

    void makeTasks(Project project) {
        var sourceSets = (SourceSetContainer)project.getExtensions().getByName("sourceSets");
        SourceSet cleanSourceSet = getCleanSourceSet().getOrNull();
        if (cleanSourceSet == null)
            cleanSourceSet = sourceSets.maybeCreate(getClean());
        SourceSet modifiedSourceSet = getModifiedSourceSet().getOrNull();
        if (modifiedSourceSet == null)
            modifiedSourceSet = sourceSets.maybeCreate(getModified());
        SourceSet patchesSourceSet = getPatchesSourceSet().getOrNull();
        if (patchesSourceSet == null)
            patchesSourceSet = sourceSets.maybeCreate(getPatches());
        SourceSet outputSourceSet = getOutputSourceSet().getOrNull();
        if (outputSourceSet == null)
            outputSourceSet = sourceSets.maybeCreate(getOutput());

        var cleanTask = project.getTasks().register(getClean()+"ExtractFromSources", DocsExtractTask.class, task -> {
            task.dependsOn(getSource());
            task.getSources().from(getSource());
            task.getOutputDirectory().set(getCleanDirectory());
            task.getReadOnly().set(true);
        });
        project.getTasks().register(getModified()+"ExtractFromSources", DocsExtractTask.class, task -> {
            task.dependsOn(getSource());
            task.getSources().from(getSource());
            task.getOutputDirectory().set(getModifiedDirectory());
        });
        project.getTasks().register(getPatches()+"GeneratePatches", MakePatchesTask.class, task -> {
            task.getClean().set(getCleanDirectory());
            task.getModified().set(getModifiedDirectory());
            task.getOutputDirectory().set(getPatchesDirectory());
        });
        project.getTasks().register(getPatches()+ StringUtils.capitalize(getOutput())+"ApplyPatches", ApplyPatchesTask.class, task -> {
            task.getPatches().set(getPatchesDirectory());
            task.getSource().set(getCleanDirectory());
            task.getOutputDirectory().set(getOutputDirectory());
            task.dependsOn(cleanTask);
        });

        cleanSourceSet.java(src -> {
            src.srcDir(cleanProperty);
        });
        modifiedSourceSet.java(src -> {
            src.srcDir(modifiedProperty);
        });
        patchesSourceSet.resources(src -> {
            src.srcDir(patchesProperty);
        });
        outputSourceSet.java(src -> {
            src.srcDir(outputProperty);
        });
    }
}
