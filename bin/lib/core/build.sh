#!/usr/bin/env bash

source "bin/init/env.sh"
export CGO_ENABLED=1
export GO386=softfloat

cd library/libcore
gomobile bind -v -trimpath -ldflags='-s -w' . || exit 1

mkdir -p "$PROJECT/app/libs"
/bin/cp -f libcore.aar "$PROJECT/app/libs"
