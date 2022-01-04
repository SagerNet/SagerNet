/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git@sekai.icu>                  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.bg.test

import cn.hutool.core.util.NumberUtil
import io.nekohasekai.sagernet.bg.AbstractInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST
import io.nekohasekai.sagernet.fmt.TAG_DNS_IN
import io.nekohasekai.sagernet.fmt.TAG_DNS_OUT
import io.nekohasekai.sagernet.fmt.TAG_SOCKS
import io.nekohasekai.sagernet.fmt.gson.gson
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig
import io.nekohasekai.sagernet.fmt.v2ray.V2RayConfig.*
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.mkPort
import libcore.Libcore
import libcore.V2RayInstance
import java.io.Closeable

class LocalDnsInstance : AbstractInstance,
    Closeable {

    lateinit var instance: V2RayInstance

    override fun launch() {
        val bind = LOCALHOST
        var directDNS = DataStore.directDns.split("\n")
            .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
        if (DataStore.useLocalDnsAsDirectDns) directDNS = listOf("localhost")
        val config = V2RayConfig().apply {
            dns = DnsObject().apply {
                servers = directDNS.map {
                    DnsObject.StringOrServerObject().apply {
                        valueY = DnsObject.ServerObject().apply {
                            address = if (!it.contains("://")) "udp+local://$it" else it
                        }
                        disableExpire = true
                    }
                }
            }
            inbounds = listOf(InboundObject().apply {
                tag = TAG_DNS_IN
                listen = bind
                port = DataStore.localDNSPort
                protocol = "dokodemo-door"
                settings = LazyInboundConfigurationObject(
                    this,
                    DokodemoDoorInboundConfigurationObject().apply {
                        address = "1.0.0.1"
                        network = "tcp,udp"
                        port = 53
                    })
            }, InboundObject().apply {
                tag = TAG_SOCKS
                listen = LOCALHOST
                port = mkPort()
                protocol = "socks"
                settings = LazyInboundConfigurationObject(
                    this,
                    SocksInboundConfigurationObject().apply {
                        auth = "noauth"
                    })
            })
            outbounds = mutableListOf()
            outbounds.add(OutboundObject().apply {
                protocol = "freedom"
                settings = LazyOutboundConfigurationObject(
                    this,
                    FreedomOutboundConfigurationObject().apply {
                        domainStrategy = "UseIP"
                    })
            })
            outbounds.add(OutboundObject().apply {
                protocol = "dns"
                tag = TAG_DNS_OUT
                settings = LazyOutboundConfigurationObject(
                    this,
                    DNSOutboundConfigurationObject().apply {
                        var dns = directDNS.first()
                        if (dns.contains(":")) {
                            val lPort = dns.substringAfterLast(":")
                            dns = dns.substringBeforeLast(":")
                            if (NumberUtil.isInteger(lPort)) {
                                port = lPort.toInt()
                            }
                        }
                        if (dns.isIpAddress()) {
                            address = dns
                        } else if (dns.contains("://")) {
                            network = "tcp"
                            address = dns.substringAfter("://")
                        }
                    })
            })
            routing = RoutingObject().apply {
                domainStrategy = "AsIs"
                rules = listOf(RoutingObject.RuleObject().apply {
                    type = "field"
                    inboundTag = listOf(TAG_DNS_IN)
                    outboundTag = TAG_DNS_OUT
                })
            }
        }
        val i = Libcore.newV2rayInstance()
        i.loadConfig(gson.toJson(config))
        i.start()

        try {
            Libcore.urlTest(i, TAG_SOCKS, DataStore.connectionTestURL, 2333)
        } catch (ignored: Exception) {
        }

        instance = i
    }

    override fun close() {
        if (::instance.isInitialized) instance.close()
    }

}