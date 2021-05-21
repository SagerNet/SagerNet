#!/bin/bash

source "bin/init/env.sh"

git submodule update --init library/shadowsocksr/src/main/jni/*
rm -rf library/shadowsocksr/build/outputs/aar
./gradlew :library:shadowsocksr:assembleRelease || exit 1
mkdir -p app/libs
cp library/shadowsocksr/build/outputs/aar/* app/libs
