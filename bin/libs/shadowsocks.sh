#!/bin/bash

source "bin/init/env.sh"

git submodule update --init shadowsocks/src/main/rust/shadowsocks-rust
rm -rf shadowsocks/build/outputs/aar
./gradlew shadowsocks:assembleRelease || exit 1
mkdir -p app/libs
cp shadowsocks/build/outputs/aar/* app/libs
