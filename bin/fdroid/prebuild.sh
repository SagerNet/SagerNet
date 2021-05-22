#/bin/bash

source "bin/init/env.sh"

git submodule update --init 'library/*'

## Install rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- --default-toolchain none -y
echo "source \$HOME/.cargo/env" >>$HOME/.bashrc
source $HOME/.cargo/env
pushd library/shadowsocks/src/main/rust/shadowsocks-rust
rustup install $(cat rust-toolchain)
rustup default $(cat rust-toolchain)
rustup target install armv7-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android
popd

echo "rust.rustcCommand=$HOME/.cargo/bin/rustc" >>local.properties
echo "rust.cargoCommand=$HOME/.cargo/bin/cargo" >>local.properties
echo "rust.pythonCommand=/usr/bin/python3" >>local.properties

# Install Golang
curl -o golang.tar.gz https://storage.googleapis.com/golang/go1.16.linux-amd64.tar.gz
mkdir "$HOME/.go"
tar -C "$HOME/.go" --strip-components=1 -xzf golang.tar.gz
rm golang.tar.gz
export PATH="$PATH:$HOME/.go/bin"
go version || exit 1

echo "sdk.dir=$ANDROID_HOME" >>local.properties
echo "ndk.dir=$ANDROID_NDK_HOME" >>local.properties

bin/lib/v2ray/init.sh
