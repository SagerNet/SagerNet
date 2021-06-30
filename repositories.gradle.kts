rootProject.extra.apply {
    set("androidPluginVersion", "4.2.1")
    set("kotlinVersion", "1.5.20")
    set("playPublisherVersion", "3.5.0")
}

repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
}
