#!/bin/bash

bin/plugin/naive/init.sh &&
  bin/plugin/naive/armeabi-v7a.sh &&
  bin/plugin/naive/arm64-v8a.sh &&
  bin/plugin/naive/x86.sh &&
  bin/plugin/naive/x86_64.sh &&
  bin/plugin/naive/end.sh
