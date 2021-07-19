#!/bin/bash

if [ -z "$ANDROID_HOME" ]; then
  if [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  elif [ -d "$HOME/.local/lib/android/sdk" ]; then
    export ANDROID_HOME="$HOME/.local/lib/android/sdk"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
  fi
fi

_NDK="$ANDROID_HOME/ndk/21.4.7075529"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_NDK_HOME"
[ -f "$_NDK/source.properties" ] || _NDK="$NDK"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_HOME/ndk-bundle"

if [ ! -f "$_NDK/source.properties" ]; then
  read -p "Enter your NDK version: " NDK_VERSION
  _NDK="$ANDROID_HOME/ndk/$NDK_VERSION"
fi

if [ ! -f "$_NDK/source.properties" ]; then
  echo "Error: NDK not found."
  exit 1
fi

export ANDROID_NDK_HOME=$_NDK
export NDK=$_NDK

if [[ "$OSTYPE" =~ ^darwin ]]; then
  export PROJECT=$PWD
else
  export PROJECT=$(realpath .)
fi

if [ ! $(command -v go) ]; then
  if [ -d /usr/lib/go-1.16 ]; then
    export PATH="$PATH:/usr/lib/go-1.16/bin"
  elif [ -d $HOME/.go ]; then
    export PATH="$PATH:$HOME/.go/bin"
  fi
fi

if [ $(command -v go) ]; then
  export PATH="$PATH:$(go env GOPATH)/bin"
fi

DEPS=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin

export ANDROID_ARM_CC=$DEPS/armv7a-linux-androideabi16-clang
export ANDROID_ARM_CXX=$DEPS/armv7a-linux-androideabi16-clang++
export ANDROID_ARM_CC_21=$DEPS/armv7a-linux-androideabi21-clang
export ANDROID_ARM_CXX_21=$DEPS/armv7a-linux-androideabi21-clang++
export ANDROID_ARM_STRIP=$DEPS/arm-linux-androideabi-strip

export ANDROID_ARM64_CC=$DEPS/aarch64-linux-android21-clang
export ANDROID_ARM64_CXX=$DEPS/aarch64-linux-android21-clang++
export ANDROID_ARM64_STRIP=$DEPS/aarch64-linux-android-strip

export ANDROID_X86_CC=$DEPS/i686-linux-android16-clang
export ANDROID_X86_CXX=$DEPS/i686-linux-android16-clang++
export ANDROID_X86_CC_21=$DEPS/i686-linux-android21-clang
export ANDROID_X86_CXX_21=$DEPS/i686-linux-android21-clang++
export ANDROID_X86_STRIP=$DEPS/i686-linux-android-strip

export ANDROID_X86_64_CC=$DEPS/x86_64-linux-android21-clang
export ANDROID_X86_64_CXX=$DEPS/x86_64-linux-android21-clang++
export ANDROID_X86_64_STRIP=$DEPS/x86_64-linux-android-strip
