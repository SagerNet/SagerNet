#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/pingtunnel/build.sh"

DIR="$ROOT/armeabi-v7a"
mkdir -p $DIR
env CC=$ANDROID_ARM_CC GOARCH=arm GOARM=7 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags="-s -w -buildid=" .
$ANDROID_ARM_STRIP $DIR/$LIB_OUTPUT