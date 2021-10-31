#/bin/bash

source "bin/init/env.sh"

git submodule update --init 'external/*'
git submodule update --init 'library/*'

bin/fdroid/install_golang.sh

echo "sdk.dir=$ANDROID_HOME" >>local.properties
echo "ndk.dir=$ANDROID_NDK_HOME" >>local.properties

bin/lib/core/init.sh
