#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/wireguard/build.sh"

DIR="$ROOT/x86_64"
mkdir -p $DIR
env CC=$ANDROID_X86_64_CC GOARCH=amd64 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags="-s -w -buildid="
$ANDROID_X86_64_STRIP $DIR/$LIB_OUTPUT
