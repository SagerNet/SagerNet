#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/naive/build.sh"

mv -f out/ReleaseArm64 out/Release || true
export EXTRA_FLAGS='target_os="android" target_cpu="arm64"'
./get-clang.sh
./build.sh
DIR="$ROOT/arm64-v8a"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT
mv out/Release out/ReleaseArm64
