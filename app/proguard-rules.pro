-repackageclasses ''
-allowaccessmodification
-keep class io.nekohasekai.sagernet.** { *;}
-keep class io.netty.channel.nio.** { *; }
-keep class io.netty.channel.socket.nio.** { *; }
-keep class io.netty.channel.epoll.** { *; }
-keep class io.netty.channel.unix.** { *; }
-keep class io.netty.handler.codec.dns.** { *; }
-keep class com.v2ray.** { *; }

# SnakeYaml
-keep class org.yaml.snakeyaml.** { *; }

-dontobfuscate
-keepattributes SourceFile