-repackageclasses ''
-allowaccessmodification
-keep class io.nekohasekai.sagernet.** { *;}
-keep class com.v2ray.** { *; }

# SnakeYaml
-keep class org.yaml.snakeyaml.** { *; }

-dontobfuscate
-keepattributes SourceFile