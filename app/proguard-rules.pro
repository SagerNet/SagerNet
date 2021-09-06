-repackageclasses ''
-allowaccessmodification
-keep class io.nekohasekai.sagernet.** { *;}

-keep class com.v2ray.core.app.observatory.** { *; }

# ini4j
-keep public class org.ini4j.spi.** { <init>(); }

# SnakeYaml
-keep class org.yaml.snakeyaml.** { *; }

-dontobfuscate
-keepattributes SourceFile