plugins {
    `java-library`
}

java {
    sourceSets.getByName("main").resources.srcDir(rootProject.file("external/Xray-core"))
}
