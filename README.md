# SagerNet for Android

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![Releases](https://img.shields.io/github/downloads/SagerNet/SagerNet/total.svg)](https://github.com/SagerNet/SagerNet/releases)
[![Language: Kotlin](https://img.shields.io/github/languages/top/SagerNet/SagerNet.svg)](https://github.com/SagerNet/SagerNet/search?l=kotlin)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

The universal proxy toolchain for Android, written in Kotlin.

## Documents

https://sagernet.org

### Protocols

The application is designed to be used whenever possible.

#### Proxy

* SOCKS (4/4a/5)
* HTTP(S)
* SSH
* Shadowsocks
* ShadowsocksR
* VMess
* VLESS
* Trojan
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
* V2Ray WebSocket browser forwarding
* Option to change the notification update interval
* A Chinese apps scanner (based on dex classpath scanning, so it may be slower)
* Proxy chain
* Balancer
* Advanced routing with outbound profile selection
* Reverse proxy
* Custom config (V2Ray / Trojan-Go)
* Traffic statistics support, including real-time display and cumulative statistics
* Foreground status based routing support

## Localization

Is SagerNet not in your language, or the translation is incorrect or incomplete? Get involved in the
translations on our [Weblate](https://hosted.weblate.org/engage/sagernet/).

[![Translation status](https://hosted.weblate.org/widgets/sagernet/-/horizontal-auto.svg)](https://hosted.weblate.org/engage/sagernet/)

### Adding a new language

First and foremost, Android must already support the specific language and locale you want to add.
We cannot work with languages that Android and the SDK do not support, the tools simply break down.
Next, if you are considering adding a country-specific variant of a language (e.g. de-AT), first
make sure that the main language is well maintained (e.g. de). Your contribution might be useful to
more people if you contribute to the existing version of your language rather than the
country-specific variant.

Anyone can create a new language via Weblate.

## Credits

#### shadowsocks-android

`The first professional proxy application on native android.`

Licensed under [GPLv3 or later][shadowsocks-android]

[shadowsocks-android]: https://github.com/shadowsocks/shadowsocks-android/blob/master/LICENSE

#### v2ray-core

`A unified platform for anti-censorship, as the core, providing routing, DNS, and more for SN.`

Licensed under [MIT][v2ray-core]

[v2ray-core]: https://github.com/shadowsocks/shadowsocks-android/blob/master/LICENSE

#### clash (OPEN SOURCE version)

`Provides built-in shadowsocks plugins, SSR and Snell support for SN.`

Licensed under [GPLv3][clash]

[clash]: https://github.com/Dreamacro/clash/blob/master/LICENSE

#### Plugins

<ul>
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