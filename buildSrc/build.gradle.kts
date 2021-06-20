plugins {
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
    implementation("cn.hutool:hutool-crypto:5.7.2")
    implementation("com.github.triplet.gradle:play-publisher:$playPublisherVersion")
    implementation("org.kohsuke:github-api:1.130")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.2")
}
