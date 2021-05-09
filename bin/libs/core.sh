#!/bin/bash

source "bin/init/env.sh"

git submodule update --init core/src/main/jni/*
rm -rf core/build/outputs/aar
./gradlew core:assembleRelease || exit 1
mkdir -p app/libs
cp core/build/outputs/aar/* app/libs
