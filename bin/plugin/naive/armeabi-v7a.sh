#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/naive/build.sh"

rm -rf out/Release
mv -f out/ReleaseArm out/Release || true
export EXTRA_FLAGS='target_os="android" target_cpu="arm"'
./get-clang.sh
./build.sh
DIR="$ROOT/armeabi-v7a"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT
mv out/Release out/ReleaseArm
