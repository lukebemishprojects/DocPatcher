# DocPatcher

DocPatcher is a tool to aid in maintenance of third-party javadocs for java libraries. It uses the the [javadoctor library](https://github.com/neoforged/javadoctor)
to generate and apply javadoc "patches" to a source. To use, you will need to add the NeoForged maven
to your plugin repositories in your `settings.gradle` file, so that javadoctor can be loaded as a dependency:

```groovy
pluginManagement {
    repositories {
        maven {
            name = "NeoForged"
            url = "https://maven.neoforged.net/releases/"
        }
    }
}
```

Then, apply the plugin:

```groovy
plugins {
    id 'dev.lukebemish.docpatcher' version '<version>'
}
```

To use, use the DSL to set up the relevant tasks:

```groovy
docPatcher.diff {
    clean = 'clean'
    modified = 'modified'
    patches = 'patches'
    output = 'output'
    outputSourceSet.set sourceSets.main
    outputDirectory.set file("build/patched")
    source = configurations.original
    missedDirectory.set file("build/missed")
}
```

The properties are documented in `DiffSettings`; the plugin generates two groups of tasks. One group is meant to set up
a new environment given a set of patches and an original, "clean" source configuration - in this case, `configurations.original`.
The other takes this envirionment and regenerates (overwriting) the patches. In this case, the main tasks to worry about include
`docPatcherSetupModifierApplyPatches`, which downloads and unpacks the clean source, applies any patches in the patches source to it,
and collects any missed patches in the specified directory. The other main tasks are `docPatcherApplyPatchesGeneratePatches`, which
generates patches by comparing the modified and clean sources, overwriting any existing patches, and `docPatcherApplyOutputApplyPatches`,
which uses the clean source and the patches to create a generated modified source. The names of these tasks will be based on the names provided
for the various `clean`, `modified`, `patches`, and `output` properties, and the directories and source sets used by these tasks can be convigured
independently by using the relevant properties in the `DiffSettings` DSL.

An example of a full configuration can be found in the `test` folder.
