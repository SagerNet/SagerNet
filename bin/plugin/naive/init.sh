#!/bin/bash

source "bin/init/env.sh"

CURR="plugin/naive"
CURR_PATH="$PROJECT/$CURR"

git submodule update --init --recursive "$CURR/*"
cd $CURR_PATH/src/main/jni/naiveproxy/src

#rm -f third_party/android_ndk
#ln -s $NDK third_party/android_ndk
#[ -f third_party/android_ndk/BUILD.gn ] || cp ../../BUILD.gn third_party/android_ndk
