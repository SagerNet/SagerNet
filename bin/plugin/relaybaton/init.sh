#!/usr/bin/env bash

source "bin/init/env.sh"

CURR="plugin/relaybaton"
CURR_PATH="$PROJECT/$CURR"

git submodule update --init "$CURR/*"
cd $CURR_PATH/src/main/go/relaybaton

export GO111MOD=on
export CGO_ENABLED=1

export GO_ROOT="$(go env GOPATH)/src/github.com/cloudflare/go"
if [ ! -d "$GO_ROOT/src" ]; then
  rm -rf $GO_ROOT
  git clone --depth 1 https://github.com/cloudflare/go.git "$GO_ROOT" || exit 1
fi

if [ ! -x "$GO_ROOT/bin/go" ]; then
  pushd $GO_ROOT/src
  ./make.bash || exit 1
  popd
fi

go mod download -x || exit 1
