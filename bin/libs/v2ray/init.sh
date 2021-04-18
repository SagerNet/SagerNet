#!/usr/bin/env bash

source "bin/init/env.sh"
export GO111MOUDLE=on
export PATH="$PATH:$(go env GOPATH)/bin"

cd $PROJECT
[ -f v2ray/go.mod ] || git submodule update --init v2ray
cd v2ray
git reset --hard && git clean -fdx
go mod download -x && go get -v golang.org/x/mobile/cmd/... || exit 1
