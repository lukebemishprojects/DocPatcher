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
    private final DirectoryProperty missedProperty;
    private final Property<SourceSet> cleanSourceSetProperty;
    private final Property<SourceSet> modifiedSourceSetProperty;
    private final Property<SourceSet> patchesSourceSetProperty;
    private final Property<SourceSet> outputSourceSetProperty;
    private final Project project;
    private String originalTag;
    private boolean sanitizeOriginal;

    public DiffSettings(ObjectFactory objectFactory, Project project) {
        this.cleanProperty = objectFactory.directoryProperty();
        this.modifiedProperty = objectFactory.directoryProperty();
        this.patchesProperty = objectFactory.directoryProperty();
        this.outputProperty = objectFactory.directoryProperty();
        this.missedProperty = objectFactory.directoryProperty();
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

    /**
     * The name to use for tasks involving the output source, as well as a default name for the output source set and folder.
     */
    public void setOutput(String output) {
        this.output = output;
        this.outputProperty.convention(project.getLayout().getProjectDirectory().dir("src").dir(output).dir("java"));
    }

    public String getPatches() {
        if (patches == null)
            throw new RuntimeException("Patches name not set");
        return patches;
    }

    /**
     * The name to use for tasks involving the patches, as well as a default name for the patches source set and folder.
     */
    public void setPatches(String patches) {
        this.patches = patches;
        this.patchesProperty.convention(project.provider(() -> {
            var dir = project.getLayout().getProjectDirectory().dir("src").dir(patches).dir("resources");
            if (!dir.getAsFile().exists())
                dir.getAsFile().mkdirs();
            return dir;
        }));
    }

    public String getModified() {
        if (modified == null)
            throw new RuntimeException("Modified name not set");
        return modified;
    }

    /**
     * The name to use for tasks involving the modified source, as well as a default name for the modified source set and folder.
     */
    public void setModified(String modified) {
        this.modified = modified;
        this.modifiedProperty.convention(project.getLayout().getProjectDirectory().dir("src").dir(modified).dir("java"));
    }

    public String getClean() {
        if (clean == null)
            throw new RuntimeException("Clean name not set");
        return clean;
    }

    /**
     * The name to use for tasks involving the clean source, as well as a default name for the clean source set and folder.
     */
    public void setClean(String clean) {
        this.clean = clean;
        this.cleanProperty.convention(project.getLayout().getProjectDirectory().dir("src").dir(clean).dir("java"));
    }

    public Configuration getSource() {
        if (source == null)
            throw new RuntimeException("Source configuration not set");
        return source;
    }

    /**
     * The configuration to pull clean files from.
     */
    public void setSource(Configuration source) {
        this.source = source;
    }

    /**
     * The directory to extract clean files to.
     */
    public DirectoryProperty getCleanDirectory() {
        return cleanProperty;
    }

    /**
     * The directory modified files are stored in, for patch generation or application.
     */
    public DirectoryProperty getModifiedDirectory() {
        return modifiedProperty;
    }

    /**
     * The directory patches are stored in, for patch generation or application.
     */
    public DirectoryProperty getPatchesDirectory() {
        return patchesProperty;
    }

    /**
     * The directory output files are stored in, for patch application.
     */
    public DirectoryProperty getOutputDirectory() {
        return outputProperty;
    }

    /**
     * The directory missed patches are placed in during patch application
     */
    public DirectoryProperty getMissedDirectory() {
        return missedProperty;
    }

    /**
     * The source set to use for the clean source.
     */
    public Property<SourceSet> getCleanSourceSet() {
        return cleanSourceSetProperty;
    }

    /**
     * The source set to use for the modified source.
     */
    public Property<SourceSet> getModifiedSourceSet() {
        return modifiedSourceSetProperty;
    }

    /**
     * The source set to use for the patch files source.
     */
    public Property<SourceSet> getPatchesSourceSet() {
        return patchesSourceSetProperty;
    }

    /**
     * The source set to use for the output source.
     */
    public Property<SourceSet> getOutputSourceSet() {
        return outputSourceSetProperty;
    }

    private static final String PREFIX_APPLY = "docPatcherApply";
    private static final String PREFIX_SETUP = "docPatcherSetup";

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

        var cleanTask = project.getTasks().register(PREFIX_SETUP+StringUtils.capitalize(getClean())+"ExtractFromSources", DocsExtractTask.class, task -> {
            task.dependsOn(getSource());
            task.getSources().from(getSource());
            task.getOutputDirectory().set(getCleanDirectory());
            task.getReadOnly().set(true);
        });
        project.getTasks().register(PREFIX_APPLY+StringUtils.capitalize(getPatches())+"GeneratePatches", MakePatchesTask.class, task -> {
            task.getClean().set(getCleanDirectory());
            task.getModified().set(getModifiedDirectory());
            task.getOutputDirectory().set(getPatchesDirectory());
        });
        project.getTasks().register(PREFIX_APPLY+StringUtils.capitalize(getOutput())+"ApplyPatches", ApplyPatchesTask.class, task -> {
            task.getPatches().set(getPatchesDirectory());
            task.getSource().set(getCleanDirectory());
            task.getOutputDirectory().set(getOutputDirectory());
            if (getOriginalTag() != null) {
                task.getOriginalTag().set(getOriginalTag());
            }
            task.getSanitizeOriginal().set(getSanitizeOriginal());
            task.dependsOn(cleanTask);
        });
        var uncheckedApplyTask = project.getTasks().register(PREFIX_SETUP+StringUtils.capitalize(getModified())+"ApplyPatchesUnchecked", ApplyPatchesTask.class, task -> {
            task.getPatches().set(getPatchesDirectory());
            task.getSource().set(getCleanDirectory());
            task.getOutputDirectory().set(getModifiedDirectory());
            task.getKeepOriginal().set(false);
            task.dependsOn(cleanTask);
        });
        project.getTasks().register(PREFIX_SETUP+StringUtils.capitalize(getModified())+"ApplyPatches", MissedPatchesTask.class, task -> {
            task.getPatches().set(getPatchesDirectory());
            task.getSource().set(getModifiedDirectory());
            task.getOutputDirectory().set(getMissedDirectory());
            task.dependsOn(uncheckedApplyTask);
        });

        cleanSourceSet.java(src ->
            src.srcDir(cleanProperty));
        modifiedSourceSet.java(src ->
            src.srcDir(modifiedProperty));
        patchesSourceSet.resources(src ->
            src.srcDir(patchesProperty));
        outputSourceSet.java(src ->
            src.srcDir(outputProperty));
    }

    public String getOriginalTag() {
        return originalTag;
    }

    public void setOriginalTag(String originalTag) {
        this.originalTag = originalTag;
    }

    public boolean getSanitizeOriginal() {
        return sanitizeOriginal;
    }

    public void setSanitizeOriginal(boolean sanitizeOriginal) {
        this.sanitizeOriginal = sanitizeOriginal;
    }
}
