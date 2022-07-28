plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("com.google.protobuf")
}

setupApp()

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    kapt.arguments {
        arg("room.incremental", true)
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    val hutoolVersion = rootProject.extra["hutoolVersion"].toString()

    implementation(fileTree("libs"))
    compileOnly(project(":library:stub"))
    implementation(project(":library:include"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.activity:activity-ktx:1.5.1")
    implementation("androidx.fragment:fragment-ktx:1.5.1")
    implementation("androidx.browser:browser:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.1")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.work:work-runtime-ktx:2.7.1")
    implementation("androidx.work:work-multiprocess:2.7.1")

    implementation(project(":external:preferencex:preferencex"))
    implementation(project(":external:preferencex:preferencex-simplemenu"))
    implementation(project(":external:preferencex:preferencex-colorpicker"))

    implementation("com.google.android.material:material:1.6.1")
    implementation("cn.hutool:hutool-core:$hutoolVersion")
    implementation("cn.hutool:hutool-json:$hutoolVersion")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.google.zxing:core:3.5.0")

    implementation("org.yaml:snakeyaml:1.30")
    implementation("com.github.daniel-stoneuk:material-about-library:3.2.0-rc01")
    implementation("com.jakewharton:process-phoenix:2.1.2")
    implementation("com.esotericsoftware:kryo:5.3.0")
    implementation("com.google.guava:guava:31.1-android")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.ini4j:ini4j:0.5.4")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("com.twofortyfouram:android-plugin-api-for-locale:1.0.4")

    val shizuku_version = "12.1.0"
    implementation("dev.rikka.shizuku:api:$shizuku_version")
    implementation("dev.rikka.shizuku:provider:$shizuku_version")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    implementation("com.simplecityapps:recyclerview-fastscroll:2.0.1") {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }
    implementation("org.smali:dexlib2:2.5.2") {
        exclude(group = "com.google.guava", module = "guava")
    }

    implementation("androidx.room:room-runtime:2.4.3")
    kapt("androidx.room:room-compiler:2.4.3")
    implementation("androidx.room:room-ktx:2.4.3")

    implementation("editorkit:editorkit:2.0.0")
    implementation("editorkit:feature-editor:2.0.0")
    implementation("editorkit:language-json:2.0.0")
    implementation("termux:terminal-view:1.0")


    implementation(project(":library:proto-stub"))
//    implementation("io.grpc:grpc-okhttp:1.40.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}

