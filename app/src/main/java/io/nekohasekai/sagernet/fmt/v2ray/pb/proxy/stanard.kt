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

package io.nekohasekai.sagernet.fmt.v2ray.pb.proxy

import com.google.protobuf.ByteString
import com.v2ray.core.app.proxyman.senderConfig
import com.v2ray.core.common.protocol.SecurityType
import com.v2ray.core.common.protocol.securityConfig
import com.v2ray.core.transport.internet.headers.http.requestConfig
import com.v2ray.core.transport.internet.headers.wireguard.WireguardConfig
import com.v2ray.core.transport.internet.kcp.encryptionSeed
import com.v2ray.core.transport.internet.streamConfig
import com.v2ray.core.transport.internet.tls.Certificate
import com.v2ray.core.transport.internet.tls.certificate
import com.v2ray.core.transport.internet.transportConfig
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean
import io.nekohasekai.sagernet.fmt.v2ray.pb.letTyped
import io.nekohasekai.sagernet.fmt.v2ray.pb.securityTypeTls
import io.nekohasekai.sagernet.fmt.v2ray.pb.tlsSettings
import io.nekohasekai.sagernet.fmt.v2ray.pb.typedMessage
import com.v2ray.core.transport.internet.grpc.config as grpcConfig
import com.v2ray.core.transport.internet.headers.http.config as headerHttpConfig
import com.v2ray.core.transport.internet.headers.http.header as httpHeader
import com.v2ray.core.transport.internet.headers.noop.Config as NoopConfig
import com.v2ray.core.transport.internet.headers.srtp.Config as SrtpConfig
import com.v2ray.core.transport.internet.headers.tls.PacketConfig as TlsPacketConfig
import com.v2ray.core.transport.internet.headers.utp.Config as UtpConfig
import com.v2ray.core.transport.internet.headers.wechat.VideoConfig as WechatVideoConfig
import com.v2ray.core.transport.internet.http.config as httpConfig
import com.v2ray.core.transport.internet.kcp.config as kcpConfig
import com.v2ray.core.transport.internet.quic.config as quicConfig
import com.v2ray.core.transport.internet.tcp.config as tcpConfig
import com.v2ray.core.transport.internet.websocket.config as wsConfig
import com.v2ray.core.transport.internet.websocket.header as websocketHeader

fun StandardV2RayBean.buildStandardSenderSettings() = letTyped {
    senderConfig {
        streamSettings = streamConfig {
            when (security) {
                "tls" -> {
                    securityType = securityTypeTls
                    tlsSettings {
                        if (sni.isNotBlank()) {
                            serverName = sni
                        }
                        if (alpn.isNotBlank()) {
                            nextProtocol.addAll(alpn.split("\n"))
                        }
                        if (certificates.isNotBlank()) {
                            disableSystemRoot = true
                            certificates.split("\n").filter { it.isNotBlank() }.forEach {
                                certificate.add(certificate {
                                    usage = Certificate.Usage.AUTHORITY_VERIFY
                                    certificate = ByteString.copyFromUtf8(it)
                                })
                            }
                        }
                        if (it.pinnedPeerCertificateChainSha256.isNotBlank()) {
                            pinnedPeerCertificateChainSha256.addAll(it.pinnedPeerCertificateChainSha256.split(
                                "\n"
                            ).filter { it.isNotBlank() }.map { ByteString.copyFromUtf8(it) })
                        }

                        if (it.allowInsecure) allowInsecure = true
                    }
                }
            }

            protocolName = it.type
            transportSettings.add(transportConfig {
                protocolName = it.type

                fun kcpQuicHeader() = typedMessage {
                    when (headerType) {
                        "srtp" -> SrtpConfig.getDefaultInstance()
                        "utp" -> UtpConfig.getDefaultInstance()
                        "wechat-video" -> WechatVideoConfig.getDefaultInstance()
                        "dtls" -> TlsPacketConfig.getDefaultInstance()
                        "wireguard" -> WireguardConfig.getDefaultInstance()
                        // "none" ->
                        else -> NoopConfig.getDefaultInstance()
                    }
                }

                when (protocolName) {
                    "tcp" -> {
                        if (it.headerType == "http") {
                            settings = typedMessage {
                                tcpConfig {
                                    headerSettings = typedMessage {
                                        headerHttpConfig {
                                            request = requestConfig {
                                                if (host.isNotBlank()) header.add(httpHeader {
                                                    name = "Host"
                                                    value.add(host)
                                                })
                                                if (path.isNotBlank()) uri.add(path)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "kcp" -> {
                        settings = typedMessage {
                            kcpConfig {
                                headerConfig = kcpQuicHeader()
                                if (mKcpSeed.isNotBlank()) {
                                    seed = encryptionSeed {
                                        seed = mKcpSeed
                                    }
                                }
                            }
                        }
                    }
                    "ws" -> {
                        settings = typedMessage {
                            wsConfig {
                                if (host.isNotBlank()) header.add(websocketHeader {
                                    name = "Host"
                                    value = host
                                })
                                path = it.path.takeIf { it.isNotBlank() } ?: "/"

                                if (it.wsMaxEarlyData > 0) maxEarlyData = it.wsMaxEarlyData
                                if (it.earlyDataHeaderName.isNotBlank()) earlyDataHeaderName = it.earlyDataHeaderName
                                if (it.wsUseBrowserForwarder) useBrowserForwarding = true
                            }
                        }
                    }
                    "http" -> {
                        settings = typedMessage {
                            httpConfig {
                                if (it.host.isNotBlank()) {
                                    host.add(it.host)
                                }

                                path = it.path.takeIf { it.isNotBlank() } ?: "/"
                            }
                        }
                    }
                    "quic" -> {
                        settings = typedMessage {
                            quicConfig {
                                security = securityConfig {
                                    type = SecurityType.valueOf(quicSecurity.uppercase())
                                }
                                key = quicKey
                                header = kcpQuicHeader()
                            }
                        }
                    }
                    "grpc" -> {
                        settings = typedMessage {
                            grpcConfig {
                                serviceName = grpcServiceName
                            }
                        }
                    }
                }
            })
        }
    }
}