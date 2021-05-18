#!/bin/bash

source "bin/init/env.sh"

ROOT="$PROJECT/naive-plugin/src/main/jniLibs"
OUTPUT="naive"
LIB_OUTPUT="lib$OUTPUT.so"

git submodule update --init --recursive 'naive-plugin/*'
cd naive-plugin/src/main/jni/naiveproxy/src

rm third_party/android_ndk
ln -s $NDK third_party/android_ndk
[ -f third_party/android_ndk/BUILD.gn ] || cp ../../BUILD.gn third_party/android_ndk
