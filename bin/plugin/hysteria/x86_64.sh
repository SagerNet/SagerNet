#!/bin/bash

source "bin/init/env.sh"
source "bin/plugin/hysteria/build.sh"

DIR="$ROOT/x86_64"
mkdir -p $DIR
env CC=$ANDROID_X86_64_CC GOARCH=amd64 go build -v -o $DIR/$LIB_OUTPUT -trimpath -ldflags "-s -w -buildid=" ./app/cmd

