#!/usr/bin/env bash

source "bin/init/env.sh"

export PATH="$PATH:$(go env GOPATH)/bin"

[ -f library/v2ray/go.mod ] || git submodule update --init library/v2ray || exit 1
cd library/v2ray
git reset --hard && git clean -fdx
go mod download -x && go get -v golang.org/x/mobile/cmd/... || exit 1
