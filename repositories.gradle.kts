rootProject.extra.apply {
    set("androidPluginVersion", "4.2.1")
    set("kotlinVersion", "1.5.10")
    set("playPublisherVersion", "3.4.0-agp4.2")
}

repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
}
