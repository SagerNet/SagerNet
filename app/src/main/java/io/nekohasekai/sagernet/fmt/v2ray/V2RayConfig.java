/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <sekai@neko.services>                    *
 * Copyright (C) 2021 by Max Lv <max.c.lv@gmail.com>                          *
 * Copyright (C) 2021 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
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

package io.nekohasekai.sagernet.fmt.v2ray;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonToken;

import java.util.List;
import java.util.Map;

import io.nekohasekai.sagernet.fmt.gson.JsonLazyInterface;
import io.nekohasekai.sagernet.fmt.gson.JsonOr;

@SuppressWarnings({"SpellCheckingInspection", "unused", "RedundantSuppression"})
public class V2RayConfig {

    public LogObject log;

    public static class LogObject {

        public String access;
        public String error;
        public String loglevel;

    }

    public ApiObject api;

    public static class ApiObject {

        public String tag;
        public List<String> services;

    }

    public DnsObject dns;

    public static class DnsObject {

        public Map<String, String> hosts;

        public List<StringOrServerObject> servers;

        public static class ServerObject {

            public String address;
            public Integer port;
            public String clientIp;
            public Boolean skipFallback;
            public List<String> domains;
            public List<String> expectIPs;

        }

        public static class StringOrServerObject extends JsonOr<String, ServerObject> {
            public StringOrServerObject() {
                super(JsonToken.STRING, JsonToken.BEGIN_OBJECT);
            }
        }

        public String clientIp;
        public Boolean disableCache;
        public String tag;
        public List<String> domains;
        public List<String> expectIPs;
        public String queryStrategy;

    }

    public RoutingObject routing;

    public static class RoutingObject {

        public String domainStrategy;
        public String domainMatcher;
        public List<RuleObject> rules;

        public static class RuleObject {

            public String type;
            public List<String> domain;
            public List<String> ip;
            public String port;
            public String sourcePort;
            public String network;
            public List<String> source;
            public List<String> user;
            public List<String> inboundTag;
            public List<String> protocol;
            public String attrs;
            public String outboundTag;
            public String balancerTag;

        }

        public List<BalancerObject> balancers;

        public static class BalancerObject {

            public String tag;
            public List<String> selector;

        }

    }

    public PolicyObject policy;

    public static class PolicyObject {

        public Map<String, LevelPolicyObject> levels;

        public static class LevelPolicyObject {

            public Integer handshake;
            public Integer connIdle;
            public Integer uplinkOnly;
            public Integer downlinkOnly;
            public Boolean statsUserUplink;
            public Boolean statsUserDownlink;
            public Integer bufferSize;

        }

        public SystemPolicyObject system;

        public static class SystemPolicyObject {

            public Boolean statsInboundUplink;
            public Boolean statsInboundDownlink;
            public Boolean statsOutboundUplink;
            public Boolean statsOutboundDownlink;

        }

    }

    public List<InboundObject> inbounds;

    public static class InboundObject {

        public String listen;
        public Integer port;
        public String protocol;
        public LazyInboundConfigurationObject settings;
        public StreamSettingsObject streamSettings;
        public String tag;
        public SniffingObject sniffing;
        public AllocateObject allocate;

        public void init() {
            if (settings != null) {
                settings.init(this);
            }
        }

        public static class SniffingObject {

            public Boolean enabled;
            public List<String> destOverride;
            public Boolean metadataOnly;

        }

        public static class AllocateObject {

            public String strategy;
            public Integer refresh;
            public Integer concurrency;

        }

    }

    public static class LazyInboundConfigurationObject extends JsonLazyInterface<InboundConfigurationObject> {

        public LazyInboundConfigurationObject() {
        }

        public LazyInboundConfigurationObject(InboundObject ctx, InboundConfigurationObject value) {
            super(value);
            init(ctx);
        }

        public InboundObject ctx;

        public void init(InboundObject ctx) {
            this.ctx = ctx;
        }

        @Nullable
        @Override
        protected Class<? extends InboundConfigurationObject> getType() {
            switch (ctx.protocol.toLowerCase()) {
                case "dokodemo-door":
                    return DokodemoDoorInboundConfigurationObject.class;
                case "http":
                    return HTTPInboundConfigurationObject.class;
                case "socks":
                    return SocksInboundConfigurationObject.class;
                case "vmess":
                    return VMessInboundConfigurationObject.class;
                case "vless":
                    return VLESSInboundConfigurationObject.class;
                case "shadowsocks":
                    return ShadowsocksInboundConfigurationObject.class;
                case "trojan":
                    return TrojanInboundConfigurationObject.class;

            }
            return null;
        }

    }

    public interface InboundConfigurationObject {
    }

    public static class DokodemoDoorInboundConfigurationObject implements InboundConfigurationObject {

        public String address;
        public Integer port;
        public String network;
        public Integer timeout;
        public Boolean followRedirect;
        public Integer userLevel;

    }

    public static class HTTPInboundConfigurationObject implements InboundConfigurationObject {

        public Integer timeout;
        public List<AccountObject> accounts;
        public Boolean allowTransparent;
        public Integer userLevel;

        public static class AccountObject {

            public String user;
            public String pass;

        }

    }

    public static class SocksInboundConfigurationObject implements InboundConfigurationObject {


        public String auth;
        public List<AccountObject> accounts;
        public Boolean udp;
        public String ip;
        public Integer userLevel;

        public static class AccountObject {

            public String user;
            public String pass;

        }

    }

    public static class VMessInboundConfigurationObject implements InboundConfigurationObject {

        public List<ClientObject> clients;
        @SerializedName("default")
        public DefaultObject defaultObject;
        public DetourObject detour;
        public Boolean disableInsecureEncryption;


        public static class ClientObject {

            public String id;
            public Integer level;
            public Integer alterId;
            public String email;

        }

        public static class DefaultObject {

            public Integer level;
            public Integer alterId;

        }

        public static class DetourObject {

            public String to;

        }

    }

    public static class VLESSInboundConfigurationObject implements InboundConfigurationObject {

        public List<ClientObject> clients;
        public String decryption;
        public List<FallbackObject> fallbacks;

        public static class ClientObject {

            public String id;
            public Integer level;
            public String email;

        }

        public static class FallbackObject {

            public String alpn;
            public String path;
            public Integer dest;
            public Integer xver;

        }

    }

    public static class ShadowsocksInboundConfigurationObject implements InboundConfigurationObject {

        public String email;
        public String method;
        public String password;
        public Integer level;
        public String network;

    }

    public static class TrojanInboundConfigurationObject implements InboundConfigurationObject {

        public List<ClientObject> clients;
        public List<FallbackObject> fallbacks;

        public static class ClientObject {

            public String password;
            public String email;
            public Integer level;

        }

        public static class FallbackObject {

            public String alpn;
            public String path;
            public Integer dest;
            public Integer xver;

        }

    }

    public List<OutboundObject> outbounds;

    public static class OutboundObject {

        public String sendThrough;
        public String protocol;
        public LazyOutboundConfigurationObject settings;
        public String tag;
        public StreamSettingsObject streamSettings;
        public ProxySettingsObject proxySettings;
        public MuxObject mux;

        public void init() {
            if (settings != null) {
                settings.init(this);
            }
        }

        public static class ProxySettingsObject {

            public String tag;
            public Boolean transportLayer;

        }

        public static class MuxObject {

            public Boolean enabled;
            public Integer concurrency;

        }

    }

    public static class LazyOutboundConfigurationObject extends JsonLazyInterface<OutboundConfigurationObject> {

        public LazyOutboundConfigurationObject() {
        }

        public LazyOutboundConfigurationObject(OutboundObject ctx, OutboundConfigurationObject value) {
            super(value);
            init(ctx);
        }

        private OutboundObject ctx;

        public void init(OutboundObject ctx) {
            this.ctx = ctx;
        }

        @Nullable
        @Override
        protected Class<? extends OutboundConfigurationObject> getType() {
            switch (ctx.protocol.toLowerCase()) {
                case "blackhole":
                    return BlackholeOutboundConfigurationObject.class;
                case "dns":
                    return DNSOutboundConfigurationObject.class;
                case "freedom":
                    return FreedomOutboundConfigurationObject.class;
                case "http":
                    return HTTPOutboundConfigurationObject.class;
                case "socks":
                    return SocksOutboundConfigurationObject.class;
                case "vmess":
                    return VMessOutboundConfigurationObject.class;
                case "vless":
                    return VLESSOutboundConfigurationObject.class;
                case "shadowsocks":
                    return ShadowsocksOutboundConfigurationObject.class;
                case "trojan":
                    return TrojanOutboundConfigurationObject.class;
                case "loopback":
                    return LoopbackOutboundConfigurationObject.class;
            }
            return null;
        }
    }

    public interface OutboundConfigurationObject {
    }

    public static class BlackholeOutboundConfigurationObject implements OutboundConfigurationObject {

        public ResponseObject response;

        public static class ResponseObject {
            public String type;
        }

    }

    public static class DNSOutboundConfigurationObject implements OutboundConfigurationObject {

        public String network;
        public String address;
        public Integer port;

    }

    public static class FreedomOutboundConfigurationObject implements OutboundConfigurationObject {

        public String domainStrategy;
        public String redirect;
        public Integer userLevel;


    }

    public static class HTTPOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> servers;

        public static class ServerObject {

            public String address;
            public Integer port;
            public List<HTTPInboundConfigurationObject.AccountObject> users;

        }

    }

    public static class SocksOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> servers;

        public static class ServerObject {

            public String address;
            public Integer port;
            public List<UserObject> users;

            public static class UserObject {

                public String user;
                public String pass;
                public Integer level;

            }

        }

    }

    public static class VMessOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> vnext;

        public static class ServerObject {

            public String address;
            public Integer port;
            public List<UserObject> users;

            public static class UserObject {

                public String id;
                public Integer alterId;
                public String security;
                public Integer level;

            }

        }

    }

    public static class ShadowsocksOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> servers;

        public static class ServerObject {

            public String address;
            public Integer port;
            public String method;
            public String password;
            public Integer level;
            public String email;

        }

    }

    public static class VLESSOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> vnext;

        public static class ServerObject {

            public String address;
            public Integer port;
            public List<UserObject> users;

            public static class UserObject {

                public String id;
                public String encryption;
                public Integer level;

            }

        }

    }

    public static class TrojanOutboundConfigurationObject implements OutboundConfigurationObject {

        public List<ServerObject> servers;

        public static class ServerObject {

            public String address;
            public Integer port;
            public String password;
            public String email;
            public Integer level;

        }

    }

    public static class LoopbackOutboundConfigurationObject implements OutboundConfigurationObject {

        public String inboundTag;

    }

    public TransportObject transport;

    public static class TransportObject {

        public TLSObject tlsSettings;
        public TcpObject tcpSettings;
        public KcpObject kcpSettings;
        public WebSocketObject wsSettings;
        public HttpObject httpSettings;
        public QuicObject quicSettings;
        public DomainSocketObject dsSettings;
        public GrpcObject grpcSettings;

    }

    public static class StreamSettingsObject {

        public String network;
        public String security;
        public TLSObject tlsSettings;
        public TcpObject tcpSettings;
        public KcpObject kcpSettings;
        public WebSocketObject wsSettings;
        public HttpObject httpSettings;
        public QuicObject quicSettings;
        public DomainSocketObject dsSettings;
        public GrpcObject grpcSettings;
        public SockoptObject sockopt;

        public static class SockoptObject {

            public Integer mark;
            public Boolean tcpFastOpen;
            public String tproxy;
            public Integer tcpKeepAliveInterval;

        }

    }

    public static class TLSObject {

        public String serverName;
        public Boolean allowInsecure;
        public List<String> alpn;
        public List<CertificateObject> certificates;
        public Boolean disableSystemRoot;
        public List<String> pinnedPeerCertificateChainSha256;

        public static class CertificateObject {

            public String usage;
            public String certificateFile;
            public String keyFile;
            public List<String> certificate;
            public List<String> key;

        }

    }

    public static class TcpObject {

        public Boolean acceptProxyProtocol;
        public HeaderObject header;

        public static class HeaderObject {

            public String type;

            public HTTPRequestObject request;
            public HTTPResponseObject response;

            public static class HTTPRequestObject {

                public String version;
                public String method;
                public List<String> path;
                public Map<String, StringOrListObject> headers;

            }

            public static class HTTPResponseObject {

                public String version;
                public String status;
                public String reason;
                public Map<String, StringOrListObject> headers;

            }

            public static class StringOrListObject extends JsonOr<String, List<String>> {
                public StringOrListObject() {
                    super(JsonToken.STRING, JsonToken.BEGIN_ARRAY);
                }
            }

        }

    }


    public static class KcpObject {

        public Integer mtu;
        public Integer tti;
        public Integer uplinkCapacity;
        public Integer downlinkCapacity;
        public Boolean congestion;
        public Integer readBufferSize;
        public Integer writeBufferSize;
        public HeaderObject header;
        public String seed;

        public static class HeaderObject {

            public String type;

        }

    }

    public static class WebSocketObject {

        public Boolean acceptProxyProtocol;
        public String path;
        public Map<String, String> headers;
        public Integer maxEarlyData;
        public String earlyDataHeaderName;
        public Boolean useBrowserForwarding;

    }

    public static class HttpObject {

        public List<String> host;
        public String path;

    }

    public static class QuicObject {

        public String security;
        public String key;
        public HeaderObject header;

        public static class HeaderObject {

            public String type;

        }

    }

    public static class DomainSocketObject {

        public String path;
        @SerializedName("abstract")
        public Boolean isAbstract;
        public Boolean padding;

    }

    public static class GrpcObject {

        public String serviceName;

    }

    public Map<String, Object> stats;

    public List<FakeDnsObject> fakedns;

    public static class FakeDnsObject {

        public String ipPool;
        public Integer poolSize;

    }

    public BrowserForwarderObject browserForwarder;

    public static class BrowserForwarderObject {

        public String listenAddr;
        public Integer listenPort;

    }

    public ReverseObject reverse;

    public static class ReverseObject {
        public List<BridgeObject> bridges;
        public List<PortalObject> portals;

        public static class BridgeObject {
            public String tag;
            public String domain;
        }

        public static class PortalObject {
            public String tag;
            public String domain;
        }
    }

    public void init() {
        if (inbounds != null) {
            for (InboundObject inbound : inbounds) inbound.init();
        }
        if (outbounds != null) {
            for (OutboundObject outbound : outbounds) outbound.init();
        }
    }

}