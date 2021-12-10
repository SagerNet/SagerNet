rootProject.extra.apply {
    set("androidPluginVersion", "7.0.4")
    set("kotlinVersion", "1.5.31")
    set("hutoolVersion", "5.7.17")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}