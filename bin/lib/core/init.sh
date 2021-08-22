#!/usr/bin/env bash

source "bin/init/env.sh"

[ -f library/libcore/go.mod ] || git submodule update --init library/libcore || exit 1
cd library/libcore
git reset --hard && git clean -fdx

./init.sh || exit 1
