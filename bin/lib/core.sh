#!/bin/bash

source "bin/init/env.sh"

git submodule update --init library/core/src/main/jni/*
rm -rf library/core/build/outputs/aar
./gradlew :library:core:assembleRelease || exit 1
mkdir -p app/libs
cp library/core/build/outputs/aar/* app/libs
