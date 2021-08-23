#!/usr/bin/env bash

source "bin/init/env.sh"

[ -f library/core/go.mod ] || git submodule update --init library/core || exit 1
cd library/core
git reset --hard && git clean -fdx

./init.sh || exit 1
