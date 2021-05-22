#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/brook/build.sh"

DIR="$ROOT/arm64-v8a"
mkdir -p $DIR
env CC=$ANDROID_ARM64_CC GOARCH=arm64 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags "-s -w -buildid=" ./cli/brook
$ANDROID_ARM64_STRIP $DIR/$LIB_OUTPUT