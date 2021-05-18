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

export EXTRA_FLAGS='target_os="android" target_cpu="arm"'
./get-clang.sh
./build.sh
DIR="$ROOT/armeabi-v7a"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT

export EXTRA_FLAGS='target_os="android" target_cpu="arm64"'
./get-clang.sh
./build.sh
DIR="$ROOT/arm64-v8a"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT

export EXTRA_FLAGS='target_os="android" target_cpu="x86"'
./get-clang.sh
./build.sh
DIR="$ROOT/x86"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT

export EXTRA_FLAGS='target_os="android" target_cpu="x64"'
./get-clang.sh
./build.sh
DIR="$ROOT/x86_64"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT

rm third_party/android_ndk
