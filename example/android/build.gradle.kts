// Mapbox Navigation SDK ships from a private Maven repo gated behind a
// *secret* downloads token (separate from the public API access token
// used at runtime). Get one from https://account.mapbox.com/access-tokens/
// (needs the DOWNLOADS:READ scope) and set it as MAPBOX_DOWNLOADS_TOKEN in
// ~/.gradle/gradle.properties (never commit it).
//
// This has to live in the CONSUMING APP's root build.gradle.kts, not the
// plugin's own build.gradle.kts - a module's `allprojects {}` only
// configures that module (it has no subprojects of its own), it doesn't
// propagate up to `:app`, which is what actually resolves the final
// dependency graph. Every app that depends on mapbox_navigation_flutter_v3
// needs this same block. See that plugin's README for the full explanation.
val mapboxDownloadsToken: String =
    (project.findProperty("MAPBOX_DOWNLOADS_TOKEN") as String?)
        ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN")
        ?: ""

allprojects {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication { create<BasicAuthentication>("basic") }
            credentials {
                username = "mapbox"
                password = mapboxDownloadsToken
            }
        }
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
