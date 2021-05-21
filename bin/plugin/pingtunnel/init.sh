#!/bin/bash

source "bin/init/env.sh"

export GO111MODULE=off
export CGO_ENABLED=1

CURR="plugin/pingtunnel"
CURR_PATH="$PROJECT/$CURR"

git submodule update --init "$CURR/*"
cd $CURR_PATH/src/main/go/pingtunnel
go get -v -t || true
