#!/bin/bash

source "bin/init/env.sh"

export GO111MODULE=off
export CGO_ENABLED=1
export GOOS=android

CURR="plugin/pingtunnel"
CURR_PATH="$PROJECT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="ptexec"
LIB_OUTPUT="lib$OUTPUT.so"

cd $CURR_PATH/src/main/go/pingtunnel