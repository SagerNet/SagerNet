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

package io.nekohasekai.sagernet.tun.ip;

import java.net.InetAddress;

public abstract class IPHeader extends Header {

    public int packetLength;

    public IPHeader(byte[] packet, int offset, int length) {
        super(packet, offset);
        packetLength = length;
    }

    public abstract int getVersion();

    public abstract int getProtocol();

    public abstract InetAddress getSourceAddress();

    public abstract void setSourceAddress(InetAddress address);

    public abstract InetAddress getDestinationAddress();

    public abstract void setDestinationAddress(InetAddress address);

    public abstract void revertAddress();

    public abstract int getHeaderLength();

    public abstract long getIPChecksum();

    public abstract void updateChecksum();

    public abstract int getDataLength();

    public abstract IPHeader copyOf();

}
