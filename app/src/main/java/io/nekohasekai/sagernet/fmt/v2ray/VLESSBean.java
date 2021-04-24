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
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class VLESSBean extends AbstractV2RayBean {

    public static VLESSBean DEFAULT_BEAN = new VLESSBean() {{
        serverPort = 1080;
        initDefaultValues();
    }};

    public String encryption;

    @Override
    public void initDefaultValues() {
        super.initDefaultValues();

        if (StrUtil.isBlank(encryption)) {
            encryption = "none";
        }
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        super.serialize(output);
        output.writeString(encryption);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        super.deserialize(input);
        encryption = input.readString();
    }

    @NotNull
    @Override
    public VLESSBean clone() {
        return KryoConverters.deserialize(new VLESSBean(), KryoConverters.serialize(this));
    }
}
