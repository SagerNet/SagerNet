#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/relaybaton/build.sh"

DIR="$ROOT/armeabi-v7a"
mkdir -p $DIR
env GOROOT="$GO_ROOT" CC=$ANDROID_ARM_CC GOARCH=arm GOARM=7 $GO_ROOT/bin/go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags="-s -w -buildid=" $PWD/cmd/cli/main.go || exit 1
$ANDROID_ARM_STRIP $DIR/$LIB_OUTPUT