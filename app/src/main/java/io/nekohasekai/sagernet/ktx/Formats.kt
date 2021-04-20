package io.nekohasekai.sagernet.ktx

import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.socks.parseSOCKS
import io.nekohasekai.sagernet.fmt.v2ray.parseVmess
import java.util.*

fun parseProxies(text: String): List<AbstractBean> {
    val links = text.split('\n').flatMap { it.trim().split(' ') }
    val entities = LinkedList<AbstractBean>()
    for (link in links) {
        if (link.startsWith("socks://")) {
            Logs.d("Try parse socks link: $link")
            runCatching {
                entities.add(parseSOCKS(link))
            }.onFailure {
                Logs.w(it)
            }
        } else if (link.startsWith("vmess://") || link.startsWith("vmess1://")) {
            Logs.d("Try parse vmess link: $link")
            runCatching {
                entities.add(parseVmess(link))
            }.onFailure {
                Logs.w(it)
            }
        } else if (link.startsWith("ss://")) {
            Logs.d("Try parse shadowsocks link: $link")
            runCatching {
                entities.add(parseShadowsocks(link))
            }.onFailure {
                Logs.w(it)
            }
        }
    }
    return entities
}