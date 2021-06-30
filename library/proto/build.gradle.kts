plugins {
    `java-library`
}

java {
    sourceSets.getByName("main").resources.srcDir(rootProject.file("external/v2ray-core"))
}
