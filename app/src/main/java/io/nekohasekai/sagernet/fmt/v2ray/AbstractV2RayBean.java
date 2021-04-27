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

public abstract class AbstractV2RayBean extends AbstractBean {


    public String uuid;

    public String network;
    public String headerType;

    public String requestHost;
    public String path;

    public String sni;
    public boolean tls;

    @Override
    public void initDefaultValues() {
        super.initDefaultValues();

        if (uuid == null) uuid = "";

        if (StrUtil.isBlank(network)) {
            network = "tcp";
        }
        if (StrUtil.isBlank(headerType)) {
            headerType = "none";
        }
        if (StrUtil.isBlank(requestHost)) {
            requestHost = "";
        }
        if (StrUtil.isBlank(path)) {
            path = "";
        }
        if (StrUtil.isBlank(sni)) {
            sni = "";
        }
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(uuid);
        output.writeString(network);
        output.writeString(headerType);

        output.writeString(requestHost);
        output.writeString(path);

        output.writeString(sni);
        output.writeBoolean(tls);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        uuid = input.readString();
        network = input.readString();
        headerType = input.readString();
        requestHost = input.readString();
        path = input.readString();
        sni = input.readString();
        tls = input.readBoolean();
    }

}
