#!/bin/bash

source "bin/init/env.sh"

CURR="plugin/naive"
CURR_PATH="$PROJECT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="naive"
LIB_OUTPUT="lib$OUTPUT.so"

git submodule update --init --recursive "$CURR/*"
cd $CURR_PATH/src/main/jni/naiveproxy/src

[ -f $NDK/BUILD.gn ] || cp ../../BUILD.gn $NDK

rm third_party/android_ndk
ln -s $NDK third_party/android_ndk

rm -rf out/Release
mv -f out/ReleaseArm out/Release &
2 >/dev/null
export EXTRA_FLAGS='target_os="android" target_cpu="arm"'
./get-clang.sh
./build.sh
DIR="$ROOT/armeabi-v7a"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT
mv out/Release out/ReleaseArm

mv -f out/ReleaseArm64 out/Release
export EXTRA_FLAGS='target_os="android" target_cpu="arm64"'
./get-clang.sh
./build.sh
DIR="$ROOT/arm64-v8a"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT
mv out/Release out/ReleaseArm64

mv -f out/ReleaseX86 out/Release
export EXTRA_FLAGS='target_os="android" target_cpu="x86"'
./get-clang.sh
./build.sh
DIR="$ROOT/x86"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT
mv out/Release out/ReleaseX86

mv -f out/ReleaseX64 out/Release
export EXTRA_FLAGS='target_os="android" target_cpu="x64"'
./get-clang.sh
./build.sh
DIR="$ROOT/x86_64"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT
mv out/Release out/ReleaseX64

rm third_party/android_ndk
rm $NDK/BUILD.gn
