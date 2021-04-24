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

package io.nekohasekai.sagernet.fmt.shadowsocksr;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class ShadowsocksRBean extends AbstractBean {

    public static ShadowsocksRBean DEFAULT_BEAN = new ShadowsocksRBean() {{
        name = "";
        serverAddress = "127.0.0.1";
        serverPort = 1080;
        password = "";
        protocol = "origin";
        protocolParam = "";
        obfs = "plain";
        obfsParam = "";
        method = "aes-256-gcm";
    }};

    public String password;
    public String method;
    public String protocol;
    public String protocolParam;
    public String obfs;
    public String obfsParam;

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(password);
        output.writeString(method);
        output.writeString(protocol);
        output.writeString(protocolParam);
        output.writeString(obfs);
        output.writeString(obfsParam);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        password = input.readString();
        method = input.readString();
        protocol = input.readString();
        protocolParam = input.readString();
        obfs = input.readString();
        obfsParam = input.readString();

    }

    @NotNull
    @Override
    public ShadowsocksRBean clone() {
        return KryoConverters.deserialize(new ShadowsocksRBean(), KryoConverters.serialize(this));
    }
}
