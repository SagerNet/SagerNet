-repackageclasses ''
-allowaccessmodification
-keep class io.nekohasekai.sagernet.** { *;}
-keep class io.netty.channel.socket.nio.** { *; }
-keep class com.v2ray.** { *; }

# SnakeYaml
-keep class org.yaml.snakeyaml.** { *; }

-dontobfuscate
-keepattributes SourceFile,LineNumberTable