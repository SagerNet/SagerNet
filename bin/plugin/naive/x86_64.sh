#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/naive/build.sh"

mv -f out/ReleaseX64 out/Release || true
export EXTRA_FLAGS='target_os="android" target_cpu="x64"'
./get-clang.sh
./build.sh
DIR="$ROOT/x86_64"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT
mv -f out/Release out/ReleaseX64
