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

_NDK="$ANDROID_HOME/ndk/23.1.7779620"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_NDK_HOME"
[ -f "$_NDK/source.properties" ] || _NDK="$NDK"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_HOME/ndk-bundle"
[ -f "$_NDK/source.properties" ] || _NDK="$ANDROID_HOME/21.4.7075529"

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
  if [ -d /usr/lib/go-1.17 ]; then
    export PATH="$PATH:/usr/lib/go-1.17/bin"
  elif [ -d $HOME/.go ]; then
    export PATH="$PATH:$HOME/.go/bin"
  fi
fi

if [ $(command -v go) ]; then
  export PATH="$PATH:$(go env GOPATH)/bin"
fi

export TOOLCHAIN=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin

export ANDROID_ARM_CC=$TOOLCHAIN/armv7a-linux-androideabi16-clang
export ANDROID_ARM_CXX=$TOOLCHAIN/armv7a-linux-androideabi16-clang++
export ANDROID_ARM_CC_21=$TOOLCHAIN/armv7a-linux-androideabi21-clang
export ANDROID_ARM_CXX_21=$TOOLCHAIN/armv7a-linux-androideabi21-clang++

export ANDROID_ARM64_CC=$TOOLCHAIN/aarch64-linux-android21-clang
export ANDROID_ARM64_CXX=$TOOLCHAIN/aarch64-linux-android21-clang++
export ANDROID_ARM64_AR=$TOOLCHAIN/aarch64-linux-android21-ar

export ANDROID_X86_CC=$TOOLCHAIN/i686-linux-android16-clang
export ANDROID_X86_CXX=$TOOLCHAIN/i686-linux-android16-clang++
export ANDROID_X86_CC_21=$TOOLCHAIN/i686-linux-android21-clang
export ANDROID_X86_CXX_21=$TOOLCHAIN/i686-linux-android21-clang++

export ANDROID_X86_64_CC=$TOOLCHAIN/x86_64-linux-android21-clang
export ANDROID_X86_64_CXX=$TOOLCHAIN/x86_64-linux-android21-clang++

export ANDROID_LD=$TOOLCHAIN/ld
export ANDROID_AR=$TOOLCHAIN/llvm-ar
