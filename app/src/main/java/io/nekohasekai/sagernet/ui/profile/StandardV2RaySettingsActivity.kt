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

package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import com.takisoft.preferencex.PreferenceFragmentCompat
import com.takisoft.preferencex.SimpleMenuPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean
import io.nekohasekai.sagernet.ktx.app

abstract class StandardV2RaySettingsActivity : ProfileSettingsActivity<StandardV2RayBean>() {

    var bean: StandardV2RayBean? = null

    override fun StandardV2RayBean.init() {
        bean = this

        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUserId = uuid
        DataStore.serverEncryption = encryption
        if (this is VMessBean) {
            DataStore.serverAlterId = alterId
        }
        DataStore.serverNetwork = type
        DataStore.serverHeader = headerType
        DataStore.serverHost = host

        when (type) {
            "kcp" -> DataStore.serverPath = mKcpSeed
            "quic" -> DataStore.serverPath = quicKey
            "grpc" -> DataStore.serverPath = grpcServiceName
            else -> DataStore.serverPath = path
        }

        DataStore.serverSecurity = security
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverPinnedCertificateChain = pinnedPeerCertificateChainSha256
        DataStore.serverQuicSecurity = quicSecurity
        DataStore.serverWsMaxEarlyData = wsMaxEarlyData
        DataStore.serverEarlyDataHeaderName = earlyDataHeaderName

        DataStore.serverWsBrowserForwarding = wsUseBrowserForwarder
        DataStore.serverAllowInsecure = allowInsecure

    }

    override fun StandardV2RayBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        uuid = DataStore.serverUserId
        encryption = DataStore.serverEncryption
        if (this is VMessBean) {
            alterId = DataStore.serverAlterId
        }
        type = DataStore.serverNetwork
        headerType = DataStore.serverHeader
        host = DataStore.serverHost
        when (type) {
            "kcp" -> mKcpSeed = DataStore.serverPath
            "quic" -> quicKey = DataStore.serverPath
            "grpc" -> grpcServiceName = DataStore.serverPath
            else -> path = DataStore.serverPath
        }
        security = DataStore.serverSecurity
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        pinnedPeerCertificateChainSha256 = DataStore.serverPinnedCertificateChain
        quicSecurity = DataStore.serverQuicSecurity
        wsMaxEarlyData = DataStore.serverWsMaxEarlyData
        earlyDataHeaderName = DataStore.serverEarlyDataHeaderName

        wsUseBrowserForwarder = DataStore.serverWsBrowserForwarding
        allowInsecure = DataStore.serverAllowInsecure
    }

    lateinit var encryption: SimpleMenuPreference
    lateinit var network: SimpleMenuPreference
    lateinit var header: SimpleMenuPreference
    lateinit var requestHost: EditTextPreference
    lateinit var path: EditTextPreference
    lateinit var quicSecurity: SimpleMenuPreference
    lateinit var security: SimpleMenuPreference

    lateinit var securityCategory: PreferenceCategory
    lateinit var wsCategory: PreferenceCategory
    lateinit var vmessExperimentsCategory: PreferenceCategory

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.standard_v2ray_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }

        encryption = findPreference(Key.SERVER_ENCRYPTION)!!
        network = findPreference(Key.SERVER_NETWORK)!!
        header = findPreference(Key.SERVER_HEADER)!!
        requestHost = findPreference(Key.SERVER_HOST)!!
        path = findPreference(Key.SERVER_PATH)!!
        quicSecurity = findPreference(Key.SERVER_QUIC_SECURITY)!!
        security = findPreference(Key.SERVER_SECURITY)!!

        securityCategory = findPreference(Key.SERVER_SECURITY_CATEGORY)!!
        wsCategory = findPreference(Key.SERVER_WS_CATEGORY)!!

        val alterId = findPreference<EditTextPreference>(Key.SERVER_ALTER_ID)!!
        if (bean is VLESSBean) {
            alterId.isVisible = false

            encryption.setEntries(R.array.vless_encryption_entry)
            encryption.setEntryValues(R.array.vless_encryption_value)

            val vev = resources.getStringArray(R.array.vless_encryption_value)
            if (encryption.value !in vev) {
                encryption.value = vev[0]
            }
        } else {
            alterId.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)

            encryption.setEntries(R.array.vmess_encryption_entry)
            encryption.setEntryValues(R.array.vmess_encryption_value)

            val vev = resources.getStringArray(R.array.vmess_encryption_value)
            if (encryption.value !in vev) {
                encryption.value = "auto"
            }
        }

        findPreference<EditTextPreference>(Key.SERVER_USER_ID)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }

        updateView(network.value)
        network.setOnPreferenceChangeListener { _, newValue ->
            updateView(newValue as String)
            true
        }

        security.setOnPreferenceChangeListener { _, newValue ->
            updateTle(newValue as String)
            true
        }

        vmessExperimentsCategory = findPreference(Key.SERVER_VMESS_EXPERIMENTS_CATEGORY)!!
        vmessExperimentsCategory.isVisible = bean is VMessBean
    }

    val tcpHeadersValue = app.resources.getStringArray(R.array.tcp_headers_value)
    val kcpQuicHeadersValue = app.resources.getStringArray(R.array.kcp_quic_headers_value)
    val quicSecurityValue = app.resources.getStringArray(R.array.quic_security_value)

    fun updateView(network: String) {
        if (bean is StandardV2RayBean) {
            when (network) {
                "tcp", "kcp" -> {
                    security.setEntries(R.array.transport_layer_encryption_entry)
                    security.setEntryValues(R.array.transport_layer_encryption_value)
                    security.value = DataStore.serverSecurity

                    val tlev =
                        resources.getStringArray(R.array.transport_layer_encryption_value)
                    if (security.value !in tlev) {
                        security.value = tlev[0]
                    }
                }
                else -> {
                    security.setEntries(R.array.transport_layer_encryption_entry)
                    security.setEntryValues(R.array.transport_layer_encryption_value)
                    security.value = DataStore.serverSecurity

                    val tlev = resources.getStringArray(R.array.transport_layer_encryption_value)
                    if (security.value !in tlev) {
                        security.value = tlev[0]
                    }
                }
            }
        }

        updateTle(security.value)

        val isQuic = network == "quic"
        val isGRPC = network == "grpc"
        val isWs = network == "ws"
        quicSecurity.isVisible = isQuic
        if (isQuic) {
            if (DataStore.serverQuicSecurity !in quicSecurityValue) {
                quicSecurity.value = quicSecurityValue[0]
            } else {
                quicSecurity.value = DataStore.serverQuicSecurity
            }
        }

        wsCategory.isVisible = isWs

        when (network) {
            "tcp" -> {
                header.setEntries(R.array.tcp_headers_entry)
                header.setEntryValues(R.array.tcp_headers_value)

                if (DataStore.serverHeader !in tcpHeadersValue) {
                    header.value = tcpHeadersValue[0]
                } else {
                    header.value = DataStore.serverHeader
                }

                var isHttp = header.value == "http"
                requestHost.isVisible = isHttp
                path.isVisible = isHttp

                header.setOnPreferenceChangeListener { _, newValue ->
                    isHttp = newValue == "http"
                    requestHost.isVisible = isHttp
                    path.isVisible = isHttp
                    true
                }

                requestHost.setTitle(R.string.http_host)
                path.setTitle(R.string.http_path)

                header.isVisible = true
            }
            "http" -> {
                requestHost.setTitle(R.string.http_host)
                path.setTitle(R.string.http_path)

                header.isVisible = false
                requestHost.isVisible = true
                path.isVisible = true
            }
            "ws" -> {
                requestHost.setTitle(R.string.ws_host)
                path.setTitle(R.string.ws_path)

                header.isVisible = false
                requestHost.isVisible = true
                path.isVisible = true
            }
            "kcp" -> {
                header.setEntries(R.array.kcp_quic_headers_entry)
                header.setEntryValues(R.array.kcp_quic_headers_value)
                path.setTitle(R.string.kcp_seed)

                if (DataStore.serverHeader !in kcpQuicHeadersValue) {
                    header.value = kcpQuicHeadersValue[0]
                } else {
                    header.value = DataStore.serverHeader
                }

                header.onPreferenceChangeListener = null

                header.isVisible = true
                requestHost.isVisible = false
                path.isVisible = true
            }
            "quic" -> {
                header.setEntries(R.array.kcp_quic_headers_entry)
                header.setEntryValues(R.array.kcp_quic_headers_value)
                path.setTitle(R.string.quic_key)

                if (DataStore.serverHeader !in kcpQuicHeadersValue) {
                    header.value = kcpQuicHeadersValue[0]
                } else {
                    header.value = DataStore.serverHeader
                }

                header.onPreferenceChangeListener = null

                header.isVisible = true
                requestHost.isVisible = false
                path.isVisible = true
            }
            "grpc" -> {
                path.setTitle(R.string.grpc_service_name)

                header.isVisible = false
                requestHost.isVisible = false
                path.isVisible = true
            }
        }
    }

    fun updateTle(tle: String) {
        val isTLS = tle == "tls"
        securityCategory.isVisible = isTLS
    }

}
