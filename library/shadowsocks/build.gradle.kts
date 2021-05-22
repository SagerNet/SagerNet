import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    id("com.android.library")
    id("org.mozilla.rust-android-gradle.rust-android")
}

setupCommon()
setupNdk()

cargo {
    module = "src/main/rust/shadowsocks-rust"
    libname = "sslocal"
    val nativeTarget = requireTargetAbi()
    targets = when (nativeTarget) {
        "armeabi-v7a" -> listOf("arm")
        "arm64-v8a" -> listOf("arm64")
        "x86" -> listOf("x86")
        "x86_64" -> listOf("x86_64")
        else -> listOf("arm", "arm64", "x86", "x86_64")
    }
    profile = findProperty("CARGO_PROFILE")?.toString() ?: "release"
    extraCargoBuildArguments = listOf("--bin", "sslocal")
    featureSpec.noDefaultBut(arrayOf(
        "local",
        "stream-cipher",
        "aead-cipher-extra",
        "logging"))
    exec = { spec, toolchain ->
        spec.environment("RUST_ANDROID_GRADLE_LINKER_WRAPPER_PY",
            "$projectDir/$module/../linker-wrapper.py")
        spec.environment("RUST_ANDROID_GRADLE_TARGET",
            "target/${toolchain.target}/$profile/lib$libname.so")
    }
}


tasks.whenTaskAdded {
    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
        dependsOn("cargoBuild")
    }
}

tasks.register<Exec>("cargoClean") {
    executable("cargo")     // cargo.cargoCommand
    args("clean")
    workingDir("$projectDir/${cargo.module}")
}

tasks.clean.dependsOn("cargoClean")