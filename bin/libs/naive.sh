#!/bin/bash

source "bin/init/env.sh"

ROOT="$PROJECT/naive-plugin/src/main/jniLibs"
OUTPUT="naive"
LIB_OUTPUT="lib$OUTPUT.so"

cd naive-plugin/src/main/jni/naiveproxy/src

export EXTRA_FLAGS='target_os="android" target_cpu="arm64"'
./get-clang.sh
./build.sh
DIR="$ROOT/arm64-v8a"
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
