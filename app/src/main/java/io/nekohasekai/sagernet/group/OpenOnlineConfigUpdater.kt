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

import android.annotation.SuppressLint
import cn.hutool.core.net.DefaultTrustManager
import cn.hutool.core.util.CharUtil
import cn.hutool.crypto.digest.DigestUtil
import cn.hutool.json.JSONObject
import com.github.shadowsocks.plugin.PluginConfiguration
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.ExtraType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.fixInvalidParams
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.USER_AGENT_ORIGIN
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocketFactory

object OpenOnlineConfigUpdater : GroupUpdater() {

    val oocConnSpec = ConnectionSpec.Builder(ConnectionSpec.RESTRICTED_TLS)
        .tlsVersions(TlsVersion.TLS_1_3)
        .build()

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        httpClient: OkHttpClient,
        byUser: Boolean
    ) {
        val apiToken: JSONObject
        var baseLink: HttpUrl
        val certSha256: String?
        try {
            apiToken = JSONObject(subscription.token)

            val version = apiToken.getInt("version")
            if (version != 1) {
                if (version != null) {
                    error("Unsupported OOC version $version")
                } else {
                    error("Missing field: version")
                }
            }
            val baseUrl = apiToken.getStr("baseUrl")
            when {
                baseUrl.isNullOrBlank() -> {
                    error("Missing field: baseUrl")
                }
                baseUrl.endsWith("/") -> {
                    error("baseUrl must not contain a trailing slash")
                }
                !baseUrl.startsWith("https://") -> {
                    error("Protocol scheme must be https")
                }
                else -> baseLink = baseUrl.toHttpUrl()
            }
            val secret = apiToken.getStr("secret")
            if (secret.isNullOrBlank()) error("Missing field: secret")
            baseLink = baseLink.newBuilder()
                .addPathSegments(secret)
                .addPathSegments("ooc/v1")
                .build()

            val userId = apiToken.getStr("userId")
            if (userId.isNullOrBlank()) error("Missing field: userId")
            baseLink = baseLink.newBuilder().addPathSegment(userId).build()
            certSha256 = apiToken.getStr("certSha256")
            if (!certSha256.isNullOrBlank()) {
                when {
                    certSha256.length != 64 -> {
                        error("certSha256 must be a SHA-256 hexadecimal string")
                    }
                    !certSha256.all { CharUtil.isLetterLower(it) || CharUtil.isNumber(it) } -> {
                        error("certSha256 must be a hexadecimal string with lowercase letters")
                    }
                }
            }
        } catch (e: Exception) {
            Logs.v("ooc token check failed, token = ${subscription.token}", e)
            error(app.getString(R.string.ooc_subscription_token_invalid))
        }

        val oocHttpClient = if (certSha256.isNullOrBlank()) httpClient else httpClient.newBuilder()
            .connectionSpecs(listOf(oocConnSpec))
            .sslSocketFactory(
                SSLSocketFactory.getDefault() as SSLSocketFactory, PinnedTrustManager(certSha256)
            )
            .build()

        val response = oocHttpClient.newCall(Request.Builder().url(baseLink).header("User-Agent",
            subscription.customUserAgent.takeIf { it.isNotBlank() } ?: USER_AGENT_ORIGIN).build())
            .execute()
            .apply {
                if (!isSuccessful) error("ERROR: HTTP $code\n\n${body?.string() ?: ""}")
                if (body == null) error("ERROR: Empty response")
            }

        Logs.d(response.toString())

        val oocResponse = JSONObject(response.body!!.string())
        subscription.username = oocResponse.getStr("username")
        subscription.bytesUsed = oocResponse.getLong("bytesUsed", -1)
        subscription.bytesRemaining = oocResponse.getLong("bytesRemaining", -1)
        subscription.expiryDate = oocResponse.getInt("expiryDate", -1)
        subscription.protocols = oocResponse.getJSONArray("protocols").filterIsInstance<String>()
        subscription.applyDefaultValues()

        for (protocol in subscription.protocols) {
            if (protocol !in supportedProtocols) {
                userInterface?.alert(app.getString(R.string.ooc_missing_protocol, protocol))
            }
        }

        var profiles = mutableListOf<AbstractBean>()

        for (protocol in subscription.protocols) {
            val profilesInProtocol = oocResponse.getJSONArray(protocol)
                .filterIsInstance<JSONObject>()

            if (protocol == "shadowsocks") for (profile in profilesInProtocol) {
                val bean = ShadowsocksBean()

                bean.name = profile.getStr("name")
                bean.serverAddress = profile.getStr("address")
                bean.serverPort = profile.getInt("port")
                bean.method = profile.getStr("method")
                bean.password = profile.getStr("password")

                val pluginName = profile.getStr("pluginName")
                if (!pluginName.isNullOrBlank()) {
                    // TODO: check plugin exists
                    // TODO: check pluginVersion
                    // TODO: support pluginArguments

                    val pl = PluginConfiguration()
                    pl.selected = pluginName
                    pl.pluginsOptions[pl.selected] = PluginOptions(profile.getStr("pluginOptions"))
                    pl.fixInvalidParams()
                    bean.plugin = pl.toString()
                }

                appendExtraInfo(profile, bean)

                profiles.add(bean.applyDefaultValues())
            }
        }

        if (subscription.forceResolve) forceResolve(httpClient, profiles, proxyGroup.id)

        val exists = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
        val duplicate = ArrayList<String>()
        if (subscription.deduplication) {
            Logs.d("Before deduplication: ${profiles.size}")
            val uniqueProfiles = LinkedHashSet<AbstractBean>()
            val uniqueNames = HashMap<AbstractBean, String>()
            for (proxy in profiles) {
                if (!uniqueProfiles.add(proxy)) {
                    val index = uniqueProfiles.indexOf(proxy)
                    if (uniqueNames.containsKey(proxy)) {
                        val name = uniqueNames[proxy]!!.replace(" ($index)", "")
                        if (name.isNotBlank()) {
                            duplicate.add("$name ($index)")
                            uniqueNames[proxy] = ""
                        }
                    }
                    duplicate.add(proxy.displayName() + " ($index)")
                } else {
                    uniqueNames[proxy] = proxy.displayName()
                }
            }
            uniqueProfiles.retainAll(uniqueNames.keys)
            profiles = uniqueProfiles.toMutableList()
        }

        Logs.d("New profiles: ${profiles.size}")

        val profileMap = profiles.associateBy { it.profileId }
        val toDelete = ArrayList<ProxyEntity>()
        val toReplace = exists.mapNotNull { entity ->
            val profileId = entity.requireBean().profileId
            if (profileMap.contains(profileId)) profileId to entity else let {
                toDelete.add(entity)
                null
            }
        }.toMap()

        Logs.d("toDelete profiles: ${toDelete.size}")
        Logs.d("toReplace profiles: ${toReplace.size}")

        val toUpdate = ArrayList<ProxyEntity>()
        val added = mutableListOf<String>()
        val updated = mutableMapOf<String, String>()
        val deleted = toDelete.map { it.displayName() }

        var userOrder = 1L
        var changed = toDelete.size
        for ((profileId, bean) in profileMap.entries) {
            val name = bean.displayName()
            if (toReplace.contains(profileId)) {
                val entity = toReplace[profileId]!!
                val existsBean = entity.requireBean()
                existsBean.applyFeatureSettings(bean)
                when {
                    existsBean != bean -> {
                        changed++
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        updated[entity.displayName()] = name

                        Logs.d("Updated profile: [$profileId] $name")
                    }
                    entity.userOrder != userOrder -> {
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        entity.userOrder = userOrder

                        Logs.d("Reordered profile: [$profileId] $name")
                    }
                    else -> {
                        Logs.d("Ignored profile: [$profileId] $name")
                    }
                }
            } else {
                changed++
                SagerDatabase.proxyDao.addProxy(ProxyEntity(
                    groupId = proxyGroup.id, userOrder = userOrder
                ).apply {
                    putBean(bean)
                })
                added.add(name)
                Logs.d("Inserted profile: $name")
            }
            userOrder++
        }

        SagerDatabase.proxyDao.updateProxy(toUpdate).also {
            Logs.d("Updated profiles: $it")
        }

        SagerDatabase.proxyDao.deleteProxy(toDelete).also {
            Logs.d("Deleted profiles: $it")
        }

        val existCount = SagerDatabase.proxyDao.countByGroup(proxyGroup.id).toInt()

        if (existCount != profileMap.size) {
            Logs.e("Exist profiles: $existCount, new profiles: ${profileMap.size}")
        }

        subscription.lastUpdated = (System.currentTimeMillis() / 1000).toInt()
        SagerDatabase.groupDao.updateGroup(proxyGroup)
        finishUpdate(proxyGroup)

        userInterface?.onUpdateSuccess(
            proxyGroup, changed, added, updated, deleted, duplicate, byUser
        )
    }

    fun appendExtraInfo(profile: JSONObject, bean: AbstractBean) {
        bean.extraType = ExtraType.OOCv1
        bean.profileId = profile.getStr("id")
        bean.group = profile.getStr("group")
        bean.owner = profile.getStr("owner")
        bean.tags = profile.getJSONArray("tags")?.filterIsInstance<String>()
    }

    val supportedProtocols = arrayOf("shadowsocks")

    @SuppressLint("CustomX509TrustManager")
    class PinnedTrustManager(val certSha256: String) : DefaultTrustManager() {

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
            val serverPK = DigestUtil.sha256Hex(chain[0].publicKey.encoded)
            if (serverPK != certSha256) throw CertificateException("Excepted certSha256 $certSha256, but was $serverPK")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            val serverPK = DigestUtil.sha256Hex(chain[0].publicKey.encoded)
            if (serverPK != certSha256) throw CertificateException("Excepted certSha256 $certSha256, but was $serverPK")
        }

    }

}