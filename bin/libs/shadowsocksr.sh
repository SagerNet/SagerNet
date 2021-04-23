#!/bin/bash

source "bin/init/env.sh"

git submodule update --init shadowsocksr/src/main/jni/*
rm -rf shadowsocksr/build/outputs/aar
./gradlew shadowsocksr:assembleRelease || exit 1
mkdir -p app/libs
cp shadowsocksr/build/outputs/aar/* app/libs
