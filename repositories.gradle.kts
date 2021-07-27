rootProject.extra.apply {
    set("androidPluginVersion", "4.2.2")
    set("kotlinVersion", "1.5.21")
    set("playPublisherVersion", "3.5.0")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}