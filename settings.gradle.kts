include(":library:core")
include(":library:shadowsocks")
include(":library:shadowsocksr")

include(":plugin:api")
include(":plugin:trojan-go")
include(":plugin:naive")
include(":plugin:pingtunnel")
include(":plugin:relaybaton")
include(":plugin:brook")

include(":external:preferencex")
include(":external:preferencex-simplemenu")
include(":external:flexbox")
include(":external:colorpicker")
include(":external:preferencex-colorpicker")

includeBuild("external/editorkit") {
    dependencySubstitution {
        substitute(module("com.blacksquircle.ui:editorkit:2.0.0")).with(project(":editorkit"))
    }
}

include(":app")
rootProject.name = "SagerNet"