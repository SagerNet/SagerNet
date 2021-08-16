#!/usr/bin/env bash

source "bin/init/env.sh"

[ -f library/libcore/go.mod ] || git submodule update --init library/libcore || exit 1
cd library/libcore
git reset --hard && git clean -fdx
go mod download -x && go get -v golang.org/x/mobile/cmd/... || exit 1
gomobile init
