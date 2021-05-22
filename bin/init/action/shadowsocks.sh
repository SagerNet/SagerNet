#!/usr/bin/env bash

curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- --default-toolchain none -y
echo "source \$HOME/.cargo/env" >>$HOME/.bashrc

git submodule update --init library/shadowsocks/src/main/rust/shadowsocks-rust
cd library/shadowsocks/src/main/rust/shadowsocks-rust
rustup target install armv7-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android
