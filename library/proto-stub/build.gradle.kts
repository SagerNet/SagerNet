import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.google.protobuf")
}

setupKotlinCommon()

val grpcVersion = "1.40.1"
val grpcKotlinVersion = "1.1.0"
val protobufVersion = "3.17.3"

dependencies {
    protobuf(project(":library:proto"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
    api("com.google.protobuf:protobuf-java:$protobufVersion")
    api("com.google.protobuf:protobuf-kotlin:$protobufVersion")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("java")
                create("kotlin")
            }
        }
    }
}
