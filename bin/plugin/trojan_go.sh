#!/usr/bin/env bash

bin/plugin/trojan_go/init.sh &&
  bin/plugin/trojan_go/armeabi-v7a.sh &&
  bin/plugin/trojan_go/arm64-v8a.sh &&
  bin/plugin/trojan_go/x86.sh &&
  bin/plugin/trojan_go/x86_64.sh &&
  bin/plugin/trojan_go/end.sh
