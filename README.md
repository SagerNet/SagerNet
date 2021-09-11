<div align="center">

# ![AnXray](https://github.com/XTLS/AnXray/raw/img/screenshots/0.png)

Another Xray for Android.

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![Releases](https://img.shields.io/github/downloads/XTLS/AnXray/total.svg)](https://github.com/XTLS/AnXray/releases)
[![Language: Kotlin](https://img.shields.io/github/languages/top/XTLS/AnXray.svg)](https://github.com/XTLS/AnXray/search?l=kotlin)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

</div>

## SCREENSHOTS

The X-style logo, slogan, and exclusive bright & dark themes designed by [RPRX](https://github.com/rprx), the Chief Visual Designer at AnXray.

<img src="https://github.com/XTLS/AnXray/raw/img/screenshots/1.jpg" width="270"> <img src="https://github.com/XTLS/AnXray/raw/img/screenshots/2.jpg" width="270"> <img src="https://github.com/XTLS/AnXray/raw/img/screenshots/3.jpg" width="270">

<img src="https://github.com/XTLS/AnXray/raw/img/screenshots/4.jpg" width="270"> <img src="https://github.com/XTLS/AnXray/raw/img/screenshots/5.jpg" width="270"> <img src="https://github.com/XTLS/AnXray/raw/img/screenshots/6.jpg" width="270">

## Documents

https://anxray.org

### Protocols

The application is designed to be used whenever possible.

#### Proxy

* SOCKS (4/4a/5)
* HTTP(S)
* SSH
* Shadowsocks
* ShadowsocksR
* VMess
* VLESS with XTLS support
* Trojan with XTLS support
* Snell
* Trojan-Go ( trojan-go-plugin )
* NaïveProxy ( naive-plugin )
* relaybaton ( relaybaton-plugin )
* Brook ( brook-plugin )
* Hysteria ( hysteria-plugin )
* WireGuard ( wireguard-plugin )

##### ROOT required

* Ping Tunnel ( pingtunnel-plugin )

#### Subscription

* Raw: All widely used formats (base64, clash or origin configuration)
* [Open Online Config](https://github.com/Shadowsocks-NET/OpenOnlineConfig)
* [Shadowsocks SIP008](https://shadowsocks.org/en/wiki/SIP008-Online-Configuration-Delivery.html)

#### Features

* Full basic features
* Xray WebSocket browser dialer
* Option to change the notification update interval
* A Chinese apps scanner (based on dex classpath scanning, so it may be slower)
* Proxy chain
* Balancer
* Advanced routing with outbound profile selection
* Reverse proxy
* Custom config (Xray / Trojan-Go)
* Traffic statistics support, including real-time display and cumulative statistics
* Foreground status based routing support

## Credits

<ul>
    <li><a href="https://github.com/shadowsocks/shadowsocks-android">shadowsocks/shadowsocks-android</a>: <code>GPL 3.0</code></li>
    <li><a href="https://github.com/shadowsocksRb/shadowsocksr-libev/blob/master/LICENSE">shadowsocksRb/shadowsocksr-libev</a>: <code>GPL 3.0</code></li>
    <li><a href="https://github.com/p4gefau1t/trojan-go/blob/master/LICENSE">p4gefau1t/Trojan-Go</a>: <code>GPL 3.0</code></li>
    <li><a href="https://github.com/klzgrad/naiveproxy/blob/master/LICENSE">klzgrad/naiveproxy</a>:  <code>BSD-3-Clause License</code></li>
    <li><a href="https://github.com/esrrhs/pingtunnel/blob/master/LICENSE">esrrhs/pingtunnel</a>:  <code>MIT</code></li>
    <li><a href="https://github.com/iyouport-org/relaybaton/blob/ech/LICENSE">iyouport-org/relaybaton</a>:  <code>MIT</code></li>
    <li><a href="https://github.com/txthinking/brook/blob/master/LICENSE">txthinking/brook</a>:  <code>GPL 3.0</code></li>
    <li><a href="https://github.com/HyNetwork/hysteria/blob/master/LICENSE.md">HyNetwork/hysteria</a>:  <code>MIT</code></li>
    <li><a href="https://github.com/WireGuard/wireguard-go/blob/master/LICENSE">WireGuard/wireguard-go</a>:  <code>MIT</code></li>

</ul>

## License

```
Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>

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