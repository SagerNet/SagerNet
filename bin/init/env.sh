#!/bin/bash

if [ -z "$ANDROID_HOME" ]; then
  if [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  elif [ -d "$HOME/.local/lib/android/sdk" ]; then
    export ANDROID_HOME="$HOME/.local/lib/android/sdk"
  fi
fi

_NDK="$ANDROID_HOME/ndk/21.4.7075529"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_NDK_HOME"
[ -f "$_NDK/source.properties" ] || _NDK="$NDK"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_HOME/ndk-bundle"

if [ ! -f "$_NDK/source.properties" ]; then
  echo "Error: NDK not found."
  exit 1
fi

export ANDROID_NDK_HOME=$_NDK
export NDK=$_NDK
export PROJECT=$(realpath .)

if [ ! $(command -v go) ]; then
  if [ -d /usr/lib/go-1.16 ]; then
    export PATH=$PATH:/usr/lib/go-1.16/bin
  elif [ -d $HOME/.go ]; then
    export PATH=$PATH:$HOME/.go/bin
  fi
fi

if [ $(command -v go) ]; then
  export PATH=$PATH:$(go env GOPATH)/bin
fi
