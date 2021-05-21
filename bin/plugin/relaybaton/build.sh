#/bin/bash

#!/usr/bin/env bash

source "bin/init/env.sh"

CURR="plugin/relaybaton"
CURR_PATH="$PROJECT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="relaybaton"
LIB_OUTPUT="lib$OUTPUT.so"

cd $CURR_PATH/src/main/go/relaybaton

export GO111MOD=on
export CGO_ENABLED=1
export GOOS=android

export GO_ROOT="$(go env GOPATH)/src/github.com/cloudflare/go"
