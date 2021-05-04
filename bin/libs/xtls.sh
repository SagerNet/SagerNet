#!/usr/bin/env bash

source "bin/init/env.sh"

export GO111MOD=on
export CGO_ENABLED=1
export GOOS=android

OUTPUT="xrayexec"
LIB_OUTPUT="lib$OUTPUT.so"
AAR_OUTPUT="$OUTPUT.aar"
ROOT="$PROJECT/xtls-plugin/src/main/jniLibs"

git submodule update --init 'xtls-plugin/*'
cd $PROJECT/xtls-plugin/src/main/go/Xray-core

DIR="$ROOT/armeabi-v7a"
mkdir -p $DIR
env CC=$ANDROID_ARM_CC GOARCH=arm GOARM=7 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags "-s -w -buildid=" ./main
$ANDROID_ARM_STRIP $DIR/$LIB_OUTPUT

DIR="$ROOT/arm64-v8a"
mkdir -p $DIR
env CC=$ANDROID_ARM64_CC GOARCH=arm64 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags "-s -w -buildid=" ./main
$ANDROID_ARM64_STRIP $DIR/$LIB_OUTPUT

DIR="$ROOT/x86"
mkdir -p $DIR
env CC=$ANDROID_X86_CC GOARCH=386 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags "-s -w -buildid=" ./main
$ANDROID_X86_STRIP $DIR/$LIB_OUTPUT

DIR="$ROOT/x86_64"
mkdir -p $DIR
env CC=$ANDROID_X86_64_CC GOARCH=amd64 go build -x -o $DIR/$LIB_OUTPUT -trimpath -ldflags "-s -w -buildid=" ./main
$ANDROID_X86_64_STRIP $DIR/$LIB_OUTPUT
