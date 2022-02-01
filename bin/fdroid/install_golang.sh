#!/bin/bash

curl -o golang.tar.gz "https://dl.google.com/go/go$1.linux-amd64.tar.gz"
mkdir "$HOME/.go"
tar -C "$HOME/.go" --strip-components=1 -xzf golang.tar.gz
rm golang.tar.gz
export PATH="$PATH:$HOME/.go/bin"
go version || exit 1