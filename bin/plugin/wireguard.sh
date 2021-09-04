#!/usr/bin/env bash

bin/plugin/wireguard/init.sh &&
  bin/plugin/wireguard/armeabi-v7a.sh &&
  bin/plugin/wireguard/arm64-v8a.sh &&
  bin/plugin/wireguard/x86.sh &&
  bin/plugin/wireguard/x86_64.sh &&
  bin/plugin/wireguard/end.sh
