#!/bin/bash

# Unfinished

source "bin/init/env.sh"

CURR="plugin/trojan"
CURR_PATH="$PROJECT/$CURR"

# git submodule update --init "$CURR/*"

BUILD_DIR=$CURR_PATH/build
cd $BUILD_DIR

if [ ! -d boost ]; then

  curl -Lo boost_1_76_0.tar.gz https://boostorg.jfrog.io/artifactory/main/release/1.76.0/source/boost_1_76_0.tar.gz
  tar -xvf boost_1_76_0.tar.gz
  mv boost_1_76_0 boost

fi

cd boost

export CXXFLAGS+=" -std=c++14"
export CC=$ANDROID_ARM_CC_21
export CXX=$ANDROID_ARM_CXX_21

echo "using clang : arm : $ANDROID_ARM_CXX_21 ; " >project-config.jam
echo "using clang : arm64 : $ANDROID_ARM64_CXX ; " >>project-config.jam
echo "using clang : x86 : $ANDROID_X86_CXX_21 ; " >>project-config.jam
echo "using clang : x64 : $ANDROID_X86_64_CXX ; " >>project-config.jam

rm -rf bin.v2

./b2 \
  --prefix="$BUILD_DIR/armeabi-v7a" \
  --with-system \
  --with-program_options \
  toolset=clang-arm \
  architecture=arm \
  variant=release \
  --layout=versioned \
  target-os=android \
  threading=multi \
  threadapi=pthread \
  cxxflags="-std=c++14" \
  link=static \
  runtime-link=static \
  install

./b2 \
  --prefix="$BUILD_DIR/arm64-v8a" \
  --with-system \
  --with-program_options \
  toolset=clang-arm64 \
  architecture=arm \
  address-model=64 \
  variant=release \
  --layout=versioned \
  target-os=android \
  threading=multi \
  threadapi=pthread \
  cxxflags="-std=c++14" \
  link=static \
  runtime-link=static \
  install

./b2 \
  --prefix="$BUILD_DIR/x86" \
  --with-system \
  --with-program_options \
  toolset=clang-x86 \
  architecture=x86 \
  variant=release \
  --layout=versioned \
  target-os=android \
  threading=multi \
  threadapi=pthread \
  cxxflags="-std=c++14" \
  link=static \
  runtime-link=static \
  install

./b2 \
  --prefix="$BUILD_DIR/x86_64" \
  --with-system \
  --with-program_options \
  toolset=clang-x64 \
  architecture=x86 \
  address-model=64 \
  variant=release \
  --layout=versioned \
  target-os=android \
  threading=multi \
  threadapi=pthread \
  cxxflags="-std=c++14" \
  link=static \
  runtime-link=static \
  install
