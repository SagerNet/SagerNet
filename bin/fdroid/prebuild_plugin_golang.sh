#!/bin/bash

git submodule update --init "plugin/$1"

bin/fdroid/install_golang.sh
