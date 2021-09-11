-repackageclasses ''
-allowaccessmodification
-keep class io.nekohasekai.sagernet.** { *;}

# ini4j
-keep public class org.ini4j.spi.** { <init>(); }

# SnakeYaml
-keep class org.yaml.snakeyaml.** { *; }

-dontobfuscate
-keepattributes SourceFile