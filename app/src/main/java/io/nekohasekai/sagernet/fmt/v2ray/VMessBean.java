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

import org.jetbrains.annotations.NotNull;

import cn.hutool.core.util.StrUtil;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class VMessBean extends AbstractBean {

    public String uuid;
    public String path;

    public String tag;
    public boolean tls;
    public String network;
    public int kcpUpLinkCapacity;
    public int kcpDownLinkCapacity;
    public String header;
    public int mux;

    // custom

    public String requestHost;
    public String sni;
    public String security;
    public int alterId;

    protected void initDefaultValues() {
        if (StrUtil.isBlank(network)) {
            network = "tls";
        }
        if (StrUtil.isBlank(security)) {
            security = "auto";
        }
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(uuid);
        output.writeString(tag);
        output.writeBoolean(tls);
        output.writeString(network);
        output.writeInt(kcpUpLinkCapacity);
        output.writeInt(kcpDownLinkCapacity);
        output.writeString(header);
        output.writeInt(mux);

        // custom
        output.writeString(requestHost);
        output.writeString(sni);
        output.writeString(security);
        output.writeInt(alterId);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        uuid = input.readString();
        tag = input.readString();
        tls = input.readBoolean();
        network = input.readString();
        kcpUpLinkCapacity = input.readInt();
        kcpDownLinkCapacity = input.readInt();
        header = input.readString();
        mux = input.readInt();

        // custom
        requestHost = input.readString();
        sni = input.readString();
        security = input.readString();
        alterId = input.readInt();
    }

    @NotNull
    @Override
    public VMessBean clone() {
        return KryoConverters.deserialize(new VMessBean(), KryoConverters.serialize(this));
    }
}
