#!/usr/bin/env bash

source "bin/init/env.sh"

CURR="plugin/tuic"
CURR_PATH="$PROJECT/$CURR"

git submodule update --init "$CURR/*"
cd $CURR_PATH/src/main/rust/tuic
