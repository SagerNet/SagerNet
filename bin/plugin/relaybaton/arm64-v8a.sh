#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/relaybaton/build.sh"

DIR="$ROOT/arm64-v8a"
mkdir -p $DIR
env GOROOT="$GO_ROOT" CC=$ANDROID_ARM64_CC GOARCH=arm64 $GO_ROOT/bin/go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags="-s -w -buildid=" $PWD/cmd/cli/main.go
$ANDROID_ARM64_STRIP $DIR/$LIB_OUTPUT