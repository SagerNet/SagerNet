plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("com.mikepenz.aboutlibraries.plugin")
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
}

dependencies {

    implementation(fileTree("libs"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0")
    api("androidx.core:core-ktx:1.5.0")
    implementation("androidx.activity:activity-ktx:1.2.3")
    implementation("androidx.fragment:fragment-ktx:1.3.4")
    implementation("androidx.browser:browser:1.3.0")

    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("androidx.appcompat:appcompat:1.3.0")

    implementation(project(":external:preferencex"))
    implementation(project(":external:preferencex-simplemenu"))
    implementation(project(":external:preferencex-colorpicker"))

    implementation("com.google.android.material:material:1.3.0")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.2")
    implementation("cn.hutool:hutool-core:5.6.5")
    implementation("cn.hutool:hutool-json:5.6.5")
    implementation("cn.hutool:hutool-crypto:5.6.5")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.google.zxing:core:3.4.1")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.2")
    implementation("org.yaml:snakeyaml:1.28")
    implementation("com.github.daniel-stoneuk:material-about-library:3.2.0-rc01")
    implementation("com.mikepenz:aboutlibraries:8.8.6")

    implementation("com.simplecityapps:recyclerview-fastscroll:2.0.1") {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }
    implementation("org.smali:dexlib2:2.5.2") {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation("com.google.guava:guava:30.1.1-android")
    implementation("com.journeyapps:zxing-android-embedded:4.2.0")

    implementation("androidx.room:room-runtime:2.3.0")
    kapt("androidx.room:room-compiler:2.3.0")
    implementation("androidx.room:room-ktx:2.3.0")

    implementation("com.github.MatrixDev.Roomigrant:RoomigrantLib:0.3.4")
    kapt("com.github.MatrixDev.Roomigrant:RoomigrantCompiler:0.3.4")

    implementation("com.esotericsoftware:kryo:5.1.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}