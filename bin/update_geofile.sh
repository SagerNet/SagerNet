#!/usr/bin/env bash

curl -L -o app/src/main/assets/geofile/geoip.dat "https://github.com/v2fly/geoip/raw/release/geoip.dat"
curl -L -o app/src/main/assets/geofile/geosite.dat "https://github.com/v2fly/domain-list-community/raw/release/dlc.dat"