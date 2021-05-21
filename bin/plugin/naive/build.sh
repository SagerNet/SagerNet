#!/bin/bash

source "bin/init/env.sh"

CURR="plugin/naive"
CURR_PATH="$PROJECT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="naive"
LIB_OUTPUT="lib$OUTPUT.so"

git submodule update --init --recursive "$CURR/*"
cd $CURR_PATH/src/main/jni/naiveproxy/src

rm third_party/android_ndk
ln -s $NDK third_party/android_ndk
[ -f third_party/android_ndk/BUILD.gn ] || cp ../../BUILD.gn third_party/android_ndk
