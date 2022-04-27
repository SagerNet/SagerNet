rootProject.extra.apply {
    set("androidPluginVersion", "7.1.3")
    set("kotlinVersion", "1.6.21")
    set("hutoolVersion", "5.7.22")
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}