#!/usr/bin/env bash

source "bin/init/env.sh"

export CGO_ENABLED=1
export GOOS=android

CURR="plugin/mieru"
CURR_PATH="$PROJECT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="mieru"
LIB_OUTPUT="lib$OUTPUT.so"

cd $CURR_PATH/src/main/go/mieru
