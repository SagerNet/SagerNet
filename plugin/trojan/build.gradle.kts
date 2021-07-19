plugins {
    id("com.android.application")
}

System.setProperty("SKIP_BUILD_TROJAN", "on")

setupCommon()
setupNdk()
setupPlugin("trojan")

android {
    defaultConfig {
        minSdkVersion(23)
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_CPP_FEATURES=rtti exceptions")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path(file("src/main/cpp/CMakeLists.txt"))
        }
    }
}