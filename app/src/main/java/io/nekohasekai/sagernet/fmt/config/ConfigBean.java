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

package io.nekohasekai.sagernet.fmt.config;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import cn.hutool.core.util.StrUtil;
import io.nekohasekai.sagernet.fmt.AbstractBean;

public class ConfigBean extends AbstractBean {

    public String type;
    public String content;

    @Override
    public String displayName() {
        if (StrUtil.isNotBlank(name)) {
            return name;
        } else {
            return "Config " + Math.abs(hashCode());
        }
    }

    @Override
    public void initDefaultValues() {
        if (name == null) name = "";
        if (type == null) type = "v2ray";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);

        output.writeString(type);
        output.writeString(content);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();

        type = input.readString();
        content = input.readString();
    }

    @NonNull
    @Override
    public AbstractBean clone() {
        return null;
    }

}
