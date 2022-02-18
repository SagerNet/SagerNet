rootProject.extra.apply {
    set("androidPluginVersion", "7.1.1")
    set("kotlinVersion", "1.6.10")
    set("hutoolVersion", "5.7.21")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}