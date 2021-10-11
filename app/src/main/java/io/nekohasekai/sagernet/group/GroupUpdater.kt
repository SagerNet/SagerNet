/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

package io.nekohasekai.sagernet.group

import io.nekohasekai.sagernet.IPv6Mode
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SubscriptionType
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.brook.BrookBean
import io.nekohasekai.sagernet.fmt.http.HttpBean
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.fmt.naive.NaiveBean
import io.nekohasekai.sagernet.fmt.relaybaton.RelayBatonBean
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.ktx.*
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet4Address
import java.net.InetAddress
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@Suppress("EXPERIMENTAL_API_USAGE")
abstract class GroupUpdater {

    abstract suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        httpClient: OkHttpClient,
        byUser: Boolean
    )

    data class Progress(
        var max: Int
    ) {
        var progress by AtomicInteger()
    }

    protected suspend fun forceResolve(
        okHttpClient: OkHttpClient, profiles: List<AbstractBean>, groupId: Long?
    ) {
        val connected = DataStore.startedProfile > 0

        var dohUrl: String? = null
        if (connected) {
            val remoteDns = DataStore.remoteDns
            when {
                remoteDns.startsWith("https+local://") -> dohUrl = remoteDns.replace(
                    "https+local://", "https://"
                )
                remoteDns.startsWith("https://") -> dohUrl = remoteDns
            }
        } else {
            val directDns = DataStore.directDns
            when {
                directDns.startsWith("https+local://") -> dohUrl = directDns.replace(
                    "https+local://", "https://"
                )
                directDns.startsWith("https://") -> dohUrl = directDns
            }
        }

        val dohHttpUrl = dohUrl?.toHttpUrlOrNull() ?: (if (connected) {
            "https://1.0.0.1/dns-query"
        } else {
            "https://223.5.5.5/dns-query"
        }).toHttpUrl()

        Logs.d("Using doh url $dohHttpUrl")

        val ipv6Mode = DataStore.ipv6Mode
        val dohClient = DnsOverHttps.Builder().client(okHttpClient).url(dohHttpUrl).apply {
            if (ipv6Mode == IPv6Mode.DISABLE) includeIPv6(false)
        }.build()
        val lookupPool = newFixedThreadPoolContext(5, "DNS Lookup")
        val lookupJobs = mutableListOf<Job>()
        val progress = Progress(profiles.size)
        if (groupId != null) {
            GroupUpdater.progress[groupId] = progress
            GroupManager.postReload(groupId)
        }
        val ipv6First = ipv6Mode >= IPv6Mode.PREFER

        for (profile in profiles) {
            when (profile) {
                // SNI rewrite unsupported
                is BrookBean -> if (profile.protocol == "wss") continue
                is NaiveBean, is RelayBatonBean -> continue
            }

            if (profile.serverAddress.isIpAddress()) continue

            lookupJobs.add(GlobalScope.launch(lookupPool) {
                try {
                    val results = dohClient.lookup(profile.serverAddress)
                    if (results.isEmpty()) error("empty response")
                    rewriteAddress(profile, results, ipv6First)
                } catch (e: Exception) {
                    Logs.d("Lookup ${profile.serverAddress} failed: ${e.readableMessage}")
                }
                if (groupId != null) {
                    progress.progress++
                    GroupManager.postReload(groupId)
                }
            })
        }

        lookupJobs.joinAll()
        lookupPool.close()
    }

    protected fun rewriteAddress(
        bean: AbstractBean, addresses: List<InetAddress>, ipv6First: Boolean
    ) {
        val address = addresses.sortedBy { (it is Inet4Address) xor ipv6First }[0].hostAddress

        with(bean) {
            when (this) {
                is SOCKSBean -> {
                    if (tls && sni.isBlank()) sni = bean.serverAddress
                }
                is HttpBean -> {
                    if (tls && sni.isBlank()) sni = bean.serverAddress
                }
                is StandardV2RayBean -> {
                    when (security) {
                        "tls" -> if (sni.isBlank()) sni = bean.serverAddress
                    }
                }
                is TrojanBean -> {
                    if (sni.isBlank()) sni = bean.serverAddress
                }
                is TrojanGoBean -> {
                    if (sni.isBlank()) sni = bean.serverAddress
                }
                is HysteriaBean -> {
                    if (sni.isBlank()) sni = bean.serverAddress
                }
            }

            bean.serverAddress = address
        }
    }

    companion object {

        val updating = Collections.synchronizedSet<Long>(mutableSetOf())
        val progress = Collections.synchronizedMap<Long, Progress>(mutableMapOf())

        fun startUpdate(proxyGroup: ProxyGroup, byUser: Boolean) {
            runOnDefaultDispatcher {
                executeUpdate(proxyGroup, byUser)
            }
        }

        suspend fun executeUpdate(proxyGroup: ProxyGroup, byUser: Boolean): Boolean {
            return coroutineScope {
                if (!updating.add(proxyGroup.id)) cancel()
                GroupManager.postReload(proxyGroup.id)

                val subscription = proxyGroup.subscription!!
                val connected = DataStore.startedProfile > 0

                val httpClient = createProxyClient()
                val userInterface = GroupManager.userInterface

                if (userInterface != null) {
                    if ((subscription.link?.startsWith("http://") == true || subscription.updateWhenConnectedOnly) && !connected) {
                        if (!userInterface.confirm(app.getString(R.string.update_subscription_warning))) {
                            finishUpdate(proxyGroup)
                            cancel()
                        }
                    }
                }

                try {
                    when (subscription.type) {
                        SubscriptionType.RAW -> RawUpdater
                        SubscriptionType.OOCv1 -> OpenOnlineConfigUpdater
                        SubscriptionType.SIP008 -> SIP008Updater
                        else -> error("wtf")
                    }.doUpdate(proxyGroup, subscription, userInterface, httpClient, byUser)
                    true
                } catch (e: Throwable) {
                    Logs.w(e)
                    userInterface?.onUpdateFailure(proxyGroup, e.readableMessage)
                    finishUpdate(proxyGroup)
                    false
                }
            }
        }


        suspend fun finishUpdate(proxyGroup: ProxyGroup) {
            updating.remove(proxyGroup.id)
            progress.remove(proxyGroup.id)
            GroupManager.postUpdate(proxyGroup)
        }

    }

}