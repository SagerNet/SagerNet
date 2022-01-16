#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/trojan_go/build.sh"

DIR="$ROOT/x86_64"
mkdir -p $DIR
env CC=$ANDROID_X86_64_CC GOARCH=amd64 go build -v -o $DIR/$LIB_OUTPUT -tags "client" -trimpath -ldflags="-s -w -buildid="

