group = "com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3"
version = "1.0-SNAPSHOT"

buildscript {
    val kotlinVersion = "2.3.20"
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.0.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

// Mapbox Navigation SDK ships from a private Maven repo gated behind a
// *secret* downloads token (separate from the public API access token
// used at runtime). Get one from https://account.mapbox.com/access-tokens/
// (needs the DOWNLOADS:READ scope) and set it as MAPBOX_DOWNLOADS_TOKEN in
// ~/.gradle/gradle.properties (never commit it). See README.md.
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

plugins {
    id("com.android.library")
}

android {
    namespace = "com.ax9labs.mapbox_navigation_flutter_v3.mapbox_navigation_flutter_v3"

    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    defaultConfig {
        minSdk = 24
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()

                it.outputs.upToDateWhen { false }

                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showStandardStreams = true
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    // MapboxManeuverView extends ConstraintLayout - ui-components only pulls
    // this in as a transitive `implementation` dep, which isn't exposed on
    // our own compile classpath, so referencing the view directly (rather
    // than just inflating it from XML) needs it declared here too.
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.mapbox.navigationcore:navigation:3.11.6")
    implementation("com.mapbox.navigationcore:ui-components:3.11.6")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.mockito:mockito-core:5.0.0")
}
