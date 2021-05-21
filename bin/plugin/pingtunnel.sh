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

git submodule update --init "$CURR/*"
cd $CURR_PATH/src/main/go/pingtunnel
go get -v -t

DIR="$ROOT/armeabi-v7a"
mkdir -p $DIR
env CC=$ANDROID_ARM_CC GOARCH=arm GOARM=7 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags="-s -w -buildid=" .
$ANDROID_ARM_STRIP $DIR/$LIB_OUTPUT

DIR="$ROOT/arm64-v8a"
mkdir -p $DIR
env CC=$ANDROID_ARM64_CC GOARCH=arm64 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags="-s -w -buildid=" .
$ANDROID_ARM64_STRIP $DIR/$LIB_OUTPUT

DIR="$ROOT/x86"
mkdir -p $DIR
env CC=$ANDROID_X86_CC GOARCH=386 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags="-s -w -buildid=" .
$ANDROID_X86_STRIP $DIR/$LIB_OUTPUT

DIR="$ROOT/x86_64"
mkdir -p $DIR
env CC=$ANDROID_X86_64_CC GOARCH=amd64 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags="-s -w -buildid=" .
$ANDROID_X86_64_STRIP $DIR/$LIB_OUTPUT
