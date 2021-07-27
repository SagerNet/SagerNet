plugins {
    kotlin("jvm") version "1.5.10"
    `java-gradle-plugin`
    `kotlin-dsl`
}

apply(from = "../repositories.gradle.kts")

dependencies {
    val androidPluginVersion = rootProject.extra["androidPluginVersion"].toString()
    val kotlinVersion = rootProject.extra["kotlinVersion"].toString()
    val playPublisherVersion = rootProject.extra["playPublisherVersion"].toString()
    implementation("com.android.tools.build:gradle:$androidPluginVersion")
    implementation("com.android.tools.build:gradle-api:$androidPluginVersion")
    implementation(kotlin("gradle-plugin", kotlinVersion))
    implementation(kotlin("stdlib"))
    implementation("cn.hutool:hutool-crypto:5.7.5")
    implementation("com.github.triplet.gradle:play-publisher:$playPublisherVersion")
    implementation("org.kohsuke:github-api:1.131")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.2")
    implementation("org.mozilla.rust-android-gradle:plugin:0.8.7")
    implementation("com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:8.9.1")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.8.17")
    implementation("com.github.ben-manes:gradle-versions-plugin:0.39.0")
}
