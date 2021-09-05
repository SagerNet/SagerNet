#!/bin/bash

pushd library/core
git fetch origin v5 || exit 1
git reset origin/v5 --hard
popd

pushd external/v2ray-core
git fetch origin dev-v5z || exit 1
git reset origin/dev-v5z --hard
popd

git add .