#!/bin/bash

pushd library/core
git fetch origin main || exit 1
git reset origin/main --hard
popd

pushd external/v2ray-core
git fetch origin dev-v5main || exit 1
git reset origin/dev-v5main --hard
popd

git add .