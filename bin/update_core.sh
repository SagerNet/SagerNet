#!/bin/bash

pushd library/core
git fetch origin main || exit 1
git reset origin/main --hard
popd

pushd external/v2ray-core
git fetch origin master || exit 1
git reset origin/master --hard
popd

git add .