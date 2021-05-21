#!/usr/bin/env bash

#
#
# Copyright (C) 2021 by nekohasekai <sekai@neko.services>
# Copyright (C) 2021 by Max Lv <max.c.lv@gmail.com>
# Copyright (C) 2021 by Mygod Studio <contact-shadowsocks-android@mygod.be>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.
#
#

source "bin/init/env.sh"

export CGO_ENABLED=1
export GOOS=android

CURR="plugin/trojan-go"
CURR_PATH="$PROJECT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="trojan-go"
LIB_OUTPUT="lib$OUTPUT.so"

git submodule update --init "$CURR/*"
cd $CURR_PATH/src/main/go/trojan-go

DIR="$ROOT/armeabi-v7a"
mkdir -p $DIR
env CC=$ANDROID_ARM_CC GOARCH=arm GOARM=7 go build -x -o $DIR/$LIB_OUTPUT -tags "client" -trimpath -ldflags="-s -w -buildid="
$ANDROID_ARM_STRIP $DIR/$LIB_OUTPUT

DIR="$ROOT/arm64-v8a"
mkdir -p $DIR
env CC=$ANDROID_ARM64_CC GOARCH=arm64 go build -x -o $DIR/$LIB_OUTPUT -tags "client" -trimpath -ldflags="-s -w -buildid="
$ANDROID_ARM64_STRIP $DIR/$LIB_OUTPUT

DIR="$ROOT/x86"
mkdir -p $DIR
env CC=$ANDROID_X86_CC GOARCH=386 go build -x -o $DIR/$LIB_OUTPUT -tags "client" -trimpath -ldflags="-s -w -buildid="
$ANDROID_X86_STRIP $DIR/$LIB_OUTPUT

DIR="$ROOT/x86_64"
mkdir -p $DIR
env CC=$ANDROID_X86_64_CC GOARCH=amd64 go build -x -o $DIR/$LIB_OUTPUT -tags "client" -trimpath -ldflags="-s -w -buildid="
$ANDROID_X86_64_STRIP $DIR/$LIB_OUTPUT
