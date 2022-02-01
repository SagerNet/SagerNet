rootProject.extra.apply {
    set("androidPluginVersion", "7.1.0")
    set("kotlinVersion", "1.6.10")
    set("hutoolVersion", "5.7.20")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}