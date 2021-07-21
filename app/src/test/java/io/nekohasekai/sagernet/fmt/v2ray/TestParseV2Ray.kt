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

package io.nekohasekai.sagernet.fmt.v2ray

import io.nekohasekai.sagernet.ktx.applyDefaultValues
import junit.framework.TestCase

class TestParseV2Ray : TestCase() {

    fun testParseV2rayNv1() {

        val address = "42.255.255.254"
        val alterId = 4
        val uuid = "59f34e8c-f310-49b0-b240-11663e365601"
        val network = "tcp"
        val port = 11451
        val comment = "日本 VIP节点5 - 10Mbps带宽 苏州-日本 IPLC-CEN专线 游戏加速用 30倍流量比例 原生日本IP落地"


        val vmess = parseV2RayN(
            "vmess://eyJhZGQiOiI0Mi4yNTUuMjU1LjI1NCIsImFpZCI6NCwiaWQiOiI1OWYzNGU4Yy1mMzEw" +
                    "LTQ5YjAtYjI0MC0xMTY2M2UzNjU2MDEiLCJuZXQiOiJ0Y3AiLCJwb3J0IjoxMTQ1MSwicHMiOiLm" +
                    "l6XmnKwgVklQ6IqC54K5NSAtIDEwTWJwc+W4puWuvSDoi4/lt54t5pel5pysIElQTEMtQ0VO5LiT" +
                    "57q/IOa4uOaIj+WKoOmAn+eUqCAzMOWAjea1gemHj+avlOS+iyDljp/nlJ/ml6XmnKxJUOiQveWc" +
                    "sCIsInRscyI6Im5vbmUiLCJ0eXBlIjoibm9uZSIsInYiOjJ9Cg=="
        )

        assertEquals(address, vmess.serverAddress)
        assertEquals(alterId, vmess.alterId)
        assertEquals(uuid, vmess.uuid)
        assertEquals(network, vmess.type)
        assertEquals(port, vmess.serverPort)
        assertEquals(comment, vmess.name)

    }

    fun testParseV2rayNv2() {

        val address = "42.255.255.254";
        val alterId = 4;
        val uuid = "59f34e8c-f310-49b0-b240-11663e365601";
        val network = "tcp";
        val port = 11451;
        val comment = "日本 VIP节点5 - 10Mbps带宽 苏州-日本 IPLC-CEN专线 游戏加速用 30倍流量比例 原生日本IP落地";

        val vmess =
            parseV2RayN("vmess://eyJhZGQiOiI0Mi4yNTUuMjU1LjI1NCIsImFpZCI6NCwiaWQiOiI1OWYzNGU4Yy1mMzEw" +
                    "LTQ5YjAtYjI0MC0xMTY2M2UzNjU2MDEiLCJuZXQiOiJ0Y3AiLCJwb3J0IjoxMTQ1MSwicHMiOiLm" +
                    "l6XmnKwgVklQ6IqC54K5NSAtIDEwTWJwc+W4puWuvSDoi4/lt54t5pel5pysIElQTEMtQ0VO5LiT" +
                    "57q/IOa4uOaIj+WKoOmAn+eUqCAzMOWAjea1gemHj+avlOS+iyDljp/nlJ/ml6XmnKxJUOiQveWc" +
                    "sCIsInRscyI6Im5vbmUiLCJ0eXBlIjoibm9uZSIsInYiOjJ9Cg==")

        assertEquals(address, vmess.serverAddress)
        assertEquals(alterId, vmess.alterId)
        assertEquals(uuid, vmess.uuid)
        assertEquals(network, vmess.type)
        assertEquals(port, vmess.serverPort)
        assertEquals(comment, vmess.name)

        vmess.initializeDefaultValues()
        assertEquals(parseV2RayN(vmess.toV2rayN()).applyDefaultValues(), vmess)

    }

    fun testParseVLESSgrpc() {
        val vless =
            parseV2Ray("vless://6d76fa31-8de2-40d4-8fee-6e61339c416f@qv2ray.net:123?type=grpc&security=tls&serviceName=FuckGFW")

        assertEquals(vless.serverAddress, "qv2ray.net")
        assertEquals(vless.serverPort, 123)
        assertEquals(vless.uuid, "6d76fa31-8de2-40d4-8fee-6e61339c416f")
        assertEquals(vless.security, "tls")
        assertEquals(vless.type, "grpc")
        assertEquals(vless.grpcServiceName, "FuckGFW")

        vless.initializeDefaultValues()
        assertEquals(parseV2Ray(vless.toUri()).applyDefaultValues(), vless)
    }

}