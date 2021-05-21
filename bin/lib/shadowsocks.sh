#!/bin/bash

source "bin/init/env.sh"

git submodule update --init library/shadowsocks/src/main/rust/shadowsocks-rust
rm -rf library/shadowsocks/build/outputs/aar
./gradlew :library:shadowsocks:assembleRelease || exit 1
mkdir -p app/libs
cp library/shadowsocks/build/outputs/aar/* app/libs
