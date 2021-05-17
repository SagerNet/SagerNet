# SagerNet for Android

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![Releases](https://img.shields.io/github/downloads/nekohasekai/SagerNet/total.svg)](https://github.com/nekohasekai/SagerNet/releases)
[![Language: Kotlin](https://img.shields.io/github/languages/top/nekohasekai/SagerNet.svg)](https://github.com/nekohasekai/SagerNet/search?l=kotlin)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

The universal proxy toolchain for Android, written in Kotlin.

## PROTOCOLS

The application is designed to be used whenever possible.

### PROXY

* SOCKS
* HTTP(S)
* Shadowsocks
* ShadowsocksR
* VMess
* VLESS
* Trojan
* VLESS / Trojan + XTLS ( xtls-plugin )
* Trojan-Go ( trojan-go-plugin )
* NaïveProxy ( naive-plugin )

### SUBSCRIPTION

* Universal base64 format
* Shadowsocks SIP008
* Just My Socks' proprietary format
* Clash

## FEATURES

* Full basic features
* V2Ray WebSocket browser forwarding
* Option to change the notification update interval
* A Chinese apps scanner (based on dex classpath scanning, so it may be slower)
* Proxy chain
* Advanced routing with outbound profile selection support

## TIPS

* Click on the title to scroll to the first proxy or the selected proxy
* Proxy list can be dragged by holding the progress bar
* The Chinese apps scanner will only scan system apps if "Show system apps" is checked

## OPEN SOURCE LICENSES

<ul>
    <li><a href="https://github.com/shadowsocks/shadowsocks-android">shadowsocks/shadowsocks-android</a>: GPL 3.0</li>
    <li><a href="https://github.com/nekohasekai/AndroidLibV2rayLite">nekohasekai/AndroidLibV2rayLite</a>: LGPL 3.0</li>
    <li><a href="https://github.com/shadowsocksRb/shadowsocksr-libev/blob/master/LICENSE">shadowsocksRb/shadowsocksr-libev</a>: GPL 3.0</li>
    <li><a href="https://github.com/XTLS/Xray-core/blob/main/LICENSE">XTLS/Xray-core</a>: MPL 2.0</li>
    <li><a href="https://github.com/p4gefau1t/trojan-go/blob/master/LICENSE">p4gefau1t/Trojan-Go</a>: GPL 3.0</li>
    <li><a href="https://github.com/klzgrad/naiveproxy/blob/master/LICENSE">klzgrad/naiveproxy</a>:  BSD-3-Clause License</li>
</ul>

### LICENSE

```
Copyright (C) 2021 by nekohasekai <sekai@neko.services>
Copyright (C) 2017-2021 by Max Lv <max.c.lv@gmail.com>
Copyright (C) 2017-2021 by Mygod Studio <contact-shadowsocks-android@mygod.be>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
```
