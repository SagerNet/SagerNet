#!/bin/bash

source "bin/init/env.sh"

git submodule update --init 'plugin/naive/*'

if [ ! -x "$HOME/.local/lib/git/bin/git" ]; then

  curl -L -o git.tar.gz https://www.kernel.org/pub/software/scm/git/git-2.31.1.tar.gz &&
    mkdir -p git &&
    tar -C git --strip-components=1 -xzf git.tar.gz &&
    pushd git &&
    make configure &&
    ./configure \
      --with-curl \
      --prefix=$HOME/.local/lib/git &&
    make install -j16 &&
    popd &&
    rm -rf git.tar.gz git

fi
