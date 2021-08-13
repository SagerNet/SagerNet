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

package io.nekohasekai.sagernet.utils

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.CorruptedFrameException
import io.netty.handler.codec.dns.*
import java.net.InetSocketAddress

object DnsParser {

    fun parseDnsQuery(recipient: InetSocketAddress, buf: ByteBuf): DnsQuery {
        val id = buf.readUnsignedShort()

        val flags = buf.readUnsignedShort()
        if (flags shr 15 == 1) {
            throw CorruptedFrameException("not a query")
        }

        val query = DatagramDnsQuery(
            null, recipient, id, DnsOpCode.QUERY
        )
        query.isRecursionDesired = (flags shr 8 and 1) == 1
        query.setZ(flags shr 4 and 0x7)

        val questionCount = buf.readUnsignedShort()
        val answerCount = buf.readUnsignedShort()
        val authorityRecordCount = buf.readUnsignedShort()
        val additionalRecordCount = buf.readUnsignedShort()
        decodeQuestions(query, buf, questionCount)
        decodeRecords(query, DnsSection.ANSWER, buf, answerCount)
        decodeRecords(query, DnsSection.AUTHORITY, buf, authorityRecordCount)
        decodeRecords(query, DnsSection.ADDITIONAL, buf, additionalRecordCount)

        return query
    }

    fun parseDnsResponse(recipient: InetSocketAddress, buffer: ByteBuf): DnsResponse {
        val id = buffer.readUnsignedShort()
        val flags = buffer.readUnsignedShort()
        if (flags shr 15 == 0) {
            throw CorruptedFrameException("not a response")
        }
        val response = DatagramDnsResponse(
            null, recipient, id, DnsOpCode.IQUERY, DnsResponseCode.valueOf(flags and 0xf)
        )
        response.isRecursionDesired = flags shr 8 and 1 == 1
        response.isAuthoritativeAnswer = flags shr 10 and 1 == 1
        response.isTruncated = flags shr 9 and 1 == 1
        response.isRecursionAvailable = flags shr 7 and 1 == 1
        response.setZ(flags shr 4 and 0x7)
        var success = false
        return try {
            val questionCount = buffer.readUnsignedShort()
            val answerCount = buffer.readUnsignedShort()
            val authorityRecordCount = buffer.readUnsignedShort()
            val additionalRecordCount = buffer.readUnsignedShort()
            decodeQuestions(response, buffer, questionCount)
            if (!decodeRecords(response, DnsSection.ANSWER, buffer, answerCount)) {
                success = true
                return response
            }
            if (!decodeRecords(response, DnsSection.AUTHORITY, buffer, authorityRecordCount)) {
                success = true
                return response
            }
            decodeRecords(response, DnsSection.ADDITIONAL, buffer, additionalRecordCount)
            success = true
            response
        } finally {
            if (!success) {
                response.release()
            }
        }
    }


    private fun decodeQuestions(query: DnsMessage, buf: ByteBuf, questionCount: Int) {
        for (i in questionCount downTo 1) {
            query.addRecord(DnsSection.QUESTION, DnsRecordDecoder.DEFAULT.decodeQuestion(buf))
        }
    }

    private fun decodeRecords(
        message: DnsMessage, section: DnsSection, buf: ByteBuf, count: Int
    ): Boolean {
        for (i in count downTo 1) {
            val r: DnsRecord = DnsRecordDecoder.DEFAULT.decodeRecord(buf) ?: // Truncated response
            return false
            message.addRecord(section, r)
        }
        return true
    }

    fun formatDnsMessage(dnsMessage: DnsMessage): String {
        var message = "#" + dnsMessage.id() + " "
        message += dnsMessage.opCode().toString() + "\n"
        message += "QUERY: " + dnsMessage.recordAt(DnsSection.QUESTION) + "\n"
        if (dnsMessage is DnsResponse) {
            message += "ANSWER: " + dnsMessage.recordAt(DnsSection.ANSWER) + "\n"
        }
        return message
    }

}