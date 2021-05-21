#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/naive/build.sh"

export EXTRA_FLAGS='target_os="android" target_cpu="x86"'
./get-clang.sh
./build.sh
DIR="$ROOT/x86"
rm -rf $DIR
mkdir -p $DIR
cp out/Release/naive $DIR/$LIB_OUTPUT
