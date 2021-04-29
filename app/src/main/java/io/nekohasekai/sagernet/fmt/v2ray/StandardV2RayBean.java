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

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import cn.hutool.core.util.StrUtil;
import io.nekohasekai.sagernet.fmt.AbstractBean;

/**
 * https://github.com/XTLS/Xray-core/issues/91
 */
public abstract class StandardV2RayBean extends AbstractBean {

    /**
     * UUID。对应配置文件该项出站中 settings.vnext[0].users[0].id 的值。
     * <p>
     * 不可省略，不能为空字符串。
     */
    public String uuid;

    /**
     * 当协议为 VMess 时，对应配置文件出站中 settings.security，可选值为 auto / aes-128-gcm / chacha20-poly1305 / none。
     * <p>
     * 省略时默认为 auto，但不可以为空字符串。除非指定为 none，否则建议省略。
     * <p>
     * 当协议为 VLESS 时，对应配置文件出站中 settings.encryption，当前可选值只有 none。
     * <p>
     * 省略时默认为 none，但不可以为空字符串。
     * <p>
     * 特殊说明：之所以不使用 security 而使用 encryption，是因为后面还有一个底层传输安全类型 security 与这个冲突。
     * 由 @huyz 提议，将此字段重命名为 encryption，这样不仅能避免命名冲突，还与 VLESS 保持了一致。
     */
    public String encryption;

    /**
     * 协议的传输方式。对应配置文件出站中 settings.vnext[0].streamSettings.network 的值。
     * <p>
     * 当前的取值必须为 tcp、kcp、ws、http、quic 其中之一，分别对应 TCP、mKCP、WebSocket、HTTP/2、QUIC 传输方式。
     */
    public String type;

    /**
     * 客户端进行 HTTP/2 通信时所发送的 Host 头部。
     * <p>
     * 省略时复用 remote-host，但不可以为空字符串。
     * <p>
     * 若有多个域名，可使用英文逗号隔开，但中间及前后不可有空格。
     * <p>
     * 必须使用 encodeURIComponent 转义。
     * -----------------------------------
     * WebSocket 请求时 Host 头的内容。不推荐省略，不推荐设为空字符串。
     * <p>
     * 必须使用 encodeURIComponent 转义。
     */
    public String host;

    /**
     * HTTP/2 的路径。省略时默认为 /，但不可以为空字符串。不推荐省略。
     * <p>
     * 必须使用 encodeURIComponent 转义。
     * -----------------------------------
     * WebSocket 的路径。省略时默认为 /，但不可以为空字符串。不推荐省略。
     * <p>
     * 必须使用 encodeURIComponent 转义。
     */
    public String path;

    /**
     * mKCP 的伪装头部类型。当前可选值有 none / srtp / utp / wechat-video / dtls / wireguard。
     * <p>
     * 省略时默认值为 none，即不使用伪装头部，但不可以为空字符串。
     * -----------------------------------
     * QUIC 的伪装头部类型。其他同 mKCP headerType 字段定义。
     */
    public String headerType;

    /**
     * mKCP 种子。省略时不使用种子，但不可以为空字符串。建议 mKCP 用户使用 seed。
     * <p>
     * 必须使用 encodeURIComponent 转义。
     */
    public String mKcpSeed;

    /**
     * QUIC 的加密方式。当前可选值有 none / aes-128-gcm / chacha20-poly1305。
     * <p>
     * 省略时默认值为 none。
     */
    public String quicSecurity;

    /**
     * 当 QUIC 的加密方式不为 none 时的加密密钥。
     * <p>
     * 当 QUIC 的加密方式为 none 时，此项不得出现；否则，此项必须出现，且不可为空字符串。
     * <p>
     * 若出现此项，则必须使用 encodeURIComponent 转义。
     */
    public String quicKey;

    /**
     * 底层传输安全 security
     * <p>
     * 设定底层传输所使用的 TLS 类型。当前可选值有 none，tls 和 xtls。
     * <p>
     * 省略时默认为 none，但不可以为空字符串。
     */
    public String security;

    /**
     * TLS SNI，对应配置文件中的 serverName 项目。
     * <p>
     * 省略时复用 remote-host，但不可以为空字符串。
     */
    public String tlsSni;

    /**
     * TLS ALPN，对应配置文件中的 alpn 项目。
     * <p>
     * 多个 ALPN 之间用英文逗号隔开，中间无空格。
     * <p>
     * 省略时由内核决定具体行为，但不可以为空字符串。
     * <p>
     * 必须使用 encodeURIComponent 转义。
     */
    public String tlsAlpn;

    // --------------------------------------- //

    public String grpcServiceName;
    public Integer wsMaxEarlyData;
    public Boolean wsUseBrowserForwarder;

    // --------------------------------------- //

    @Override
    public void initDefaultValues() {
        super.initDefaultValues();

        if (StrUtil.isBlank(uuid)) uuid = "";

        if (StrUtil.isBlank(type)) type = "tcp";
        if (StrUtil.isBlank(host)) host = "";
        if (StrUtil.isBlank(path)) path = "";
        if (StrUtil.isBlank(headerType)) headerType = "";
        if (StrUtil.isBlank(mKcpSeed)) mKcpSeed = "";
        if (StrUtil.isBlank(quicSecurity)) quicSecurity = "";
        if (StrUtil.isBlank(quicKey)) quicKey = "";

        if (StrUtil.isBlank(security)) security = "";
        if (StrUtil.isBlank(tlsSni)) tlsSni = "";
        if (StrUtil.isBlank(tlsAlpn)) tlsAlpn = "";

        if (StrUtil.isBlank(grpcServiceName)) grpcServiceName = "";
        if (wsMaxEarlyData == null) wsMaxEarlyData = 0;
        if (wsUseBrowserForwarder == null) wsUseBrowserForwarder = false;

    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);

        output.writeString(uuid);
        output.writeString(encryption);
        output.writeString(type);

        switch (type) {
            case "tcp": {
                break;
            }
            case "kcp": {
                output.writeString(headerType);
                output.writeString(mKcpSeed);
                break;
            }
            case "ws": {
                output.writeString(host);
                output.writeString(path);
                output.writeInt(wsMaxEarlyData);
                output.writeBoolean(wsUseBrowserForwarder);
                break;
            }
            case "http": {
                output.writeString(host);
                output.writeString(path);
                break;
            }
            case "quic": {
                output.writeString(headerType);
                output.writeString(quicSecurity);
                output.writeString(quicKey);
            }
            case "grpc": {
                output.writeString(grpcServiceName);
            }
        }

        output.writeString(security);

        //noinspection SwitchStatementWithTooFewBranches
        switch (security) {
            case "tls": {
                output.writeString(tlsSni);
                output.writeString(tlsAlpn);
                break;
            }
        }
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        uuid = input.readString();
        encryption = input.readString();
        type = input.readString();

        switch (type) {
            case "tcp": {
                break;
            }
            case "kcp": {
                headerType = input.readString();
                mKcpSeed = input.readString();
                break;
            }
            case "ws": {
                host = input.readString();
                path = input.readString();
                wsMaxEarlyData = input.readInt();
                wsUseBrowserForwarder = input.readBoolean();
                break;
            }
            case "http": {
                host = input.readString();
                path = input.readString();
                break;
            }
            case "quic": {
                headerType = input.readString();
                quicSecurity = input.readString();
                quicKey = input.readString();
            }
            case "grpc": {
                grpcServiceName = input.readString();
            }
        }

        security = input.readString();
        //noinspection SwitchStatementWithTooFewBranches
        switch (security) {
            case "tls": {
                tlsSni = input.readString();
                tlsAlpn = input.readString();
                break;
            }
        }

        initDefaultValues();
    }

}
