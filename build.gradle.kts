import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    id("com.github.ben-manes.versions") version "0.39.0" apply false
}

buildscript {
    apply(from = "repositories.gradle.kts")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        val androidPluginVersion = rootProject.extra["androidPluginVersion"].toString()
        val kotlinVersion = rootProject.extra["kotlinVersion"].toString()
        val playPublisherVersion = rootProject.extra["playPublisherVersion"].toString()

        classpath("com.android.tools.build:gradle:$androidPluginVersion")
        classpath(kotlin("gradle-plugin", kotlinVersion))
        classpath("org.mozilla.rust-android-gradle:plugin:0.8.6")
        classpath("com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:8.9.0")
        classpath("com.github.triplet.gradle:play-publisher:$playPublisherVersion")
    }
}

allprojects {
    apply(from = "${rootProject.projectDir}/repositories.gradle.kts")
    apply(plugin = "com.github.ben-manes.versions")
    tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
        resolutionStrategy {
            componentSelection {
                all {
                    val rejected = listOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea")
                        .map { qualifier -> Regex("(?i).*[.-]$qualifier[.\\d-+]*") }
                        .any { it.matches(candidate.version) }
                    if (rejected) {
                        reject("Release candidate")
                    }
                }
            }
        }
        // optional parameters
        checkForGradleUpdate = true
        outputFormatter = "json"
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

// skip uploading the mapping to Crashlytics
subprojects {
    tasks.whenTaskAdded {
        if (name.contains("uploadCrashlyticsMappingFile")) enabled = false
    }
}
