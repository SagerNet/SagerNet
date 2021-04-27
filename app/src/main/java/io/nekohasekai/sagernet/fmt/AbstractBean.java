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

package io.nekohasekai.sagernet.fmt;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import cn.hutool.core.clone.Cloneable;
import cn.hutool.core.util.StrUtil;

public abstract class AbstractBean implements Cloneable<AbstractBean> {

    public String serverAddress;
    public int serverPort;
    public String name;

    public void initDefaultValues() {
        if (StrUtil.isBlank(serverAddress)) {
            serverAddress = "127.0.0.1";
        }
        if (serverPort == 0) {
            serverPort = 1080;
        }
        if (name == null) name = "";
    }

    public void serialize(ByteBufferOutput output) {
        output.writeString(name);
        output.writeString(serverAddress);
        output.writeInt(serverPort);
    }

    public void deserialize(ByteBufferInput input) {
        name = input.readString();
        serverAddress = input.readString();
        serverPort = input.readInt();
    }

    @NotNull
    @Override
    public abstract AbstractBean clone();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Arrays.equals(KryoConverters.serialize(this), KryoConverters.serialize((AbstractBean) o));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(KryoConverters.serialize(this));
    }

}
