plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("com.mikepenz.aboutlibraries.plugin")
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

    implementation(fileTree("libs"))

    compileOnly(project(":library:include"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.1")
    implementation("androidx.core:core-ktx:1.6.0")
    implementation("androidx.activity:activity-ktx:1.2.4")
    implementation("androidx.fragment:fragment-ktx:1.3.6")
    implementation("androidx.browser:browser:1.3.0")

    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.work:work-runtime-ktx:2.5.0")
    implementation("androidx.work:work-multiprocess:2.5.0")


    implementation(project(":external:preferencex:preferencex"))
    implementation(project(":external:preferencex:preferencex-simplemenu"))
    implementation(project(":external:preferencex:preferencex-colorpicker"))

    implementation("com.google.android.material:material:1.4.0")
    implementation("cn.hutool:hutool-core:5.7.5")
    implementation("cn.hutool:hutool-cache:5.7.5")
    implementation("cn.hutool:hutool-json:5.7.5")
    implementation("cn.hutool:hutool-crypto:5.7.5")
    implementation("com.google.code.gson:gson:2.8.7")
    implementation("com.google.zxing:core:3.4.1")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.2")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.0.0-alpha.2")
    implementation("org.yaml:snakeyaml:1.29")
    implementation("com.github.daniel-stoneuk:material-about-library:3.2.0-rc01")
    implementation("com.mikepenz:aboutlibraries:8.9.1")

    implementation("com.simplecityapps:recyclerview-fastscroll:2.0.1") {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }
    implementation("org.smali:dexlib2:2.5.2") {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation("org.conscrypt:conscrypt-android:2.5.2")
    implementation("com.google.guava:guava:30.1.1-android")
    implementation("com.journeyapps:zxing-android-embedded:4.2.0")

    implementation("androidx.room:room-runtime:2.3.0")
    kapt("androidx.room:room-compiler:2.3.0")
    implementation("androidx.room:room-ktx:2.3.0")

    implementation("com.github.MatrixDev.Roomigrant:RoomigrantLib:0.3.4")
    kapt("com.github.MatrixDev.Roomigrant:RoomigrantCompiler:0.3.4")

    implementation("com.esotericsoftware:kryo:5.1.1")
    implementation("editorkit:editorkit:2.0.0")
    implementation("editorkit:feature-editor:2.0.0")
    implementation("editorkit:language-json:2.0.0")

    implementation(project(":library:proto-stub"))
    implementation("io.grpc:grpc-okhttp:1.39.0")
    implementation("io.netty:netty-all:4.1.66.Final")
    implementation("org.slf4j:slf4j-simple:1.7.32")
    implementation("com.github.seancfoley:ipaddress:5.3.3")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:2.0")
    implementation("dnsjava:dnsjava:3.4.0")
    implementation("com.github.topjohnwu.libsu:io:3.1.2")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}

