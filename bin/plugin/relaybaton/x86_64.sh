#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/relaybaton/build.sh"

DIR="$ROOT/x86_64"
mkdir -p $DIR
env GOROOT="$GO_ROOT" CC=$ANDROID_X86_64_CC GOARCH=amd64 $GO_ROOT/bin/go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags="-s -w -buildid=" $PWD/cmd/cli/main.go
$ANDROID_X86_64_STRIP $DIR/$LIB_OUTPUT
