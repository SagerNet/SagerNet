#!/usr/bin/env bash

source "bin/init/env.sh"

CURR="plugin/tuic"
CURR_PATH="$PROJECT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="tuic"
LIB_OUTPUT="lib$OUTPUT.so"

cd $CURR_PATH/src/main/rust/tuic

export AR=$ANDROID_AR
export LD=$ANDROID_LD

ndkVer=$(grep Pkg.Revision $ANDROID_NDK_HOME/source.properties)
ndkVer=${ndkVer#*= }
ndkVer=${ndkVer%%.*}

export CARGO_NDK_MAJOR_VERSION=$ndkVer
export RUST_ANDROID_GRADLE_CC_LINK_ARG="-Wl"
export RUST_ANDROID_GRADLE_PYTHON_COMMAND=python
export RUST_ANDROID_GRADLE_LINKER_WRAPPER_PY=$PROJECT/bin/rust-linker/linker-wrapper.py
