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

import androidx.room.TypeConverter;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean;
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean;
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean;
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean;
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean;
import io.nekohasekai.sagernet.ktx.KryosKt;

public class KryoConverters {

    private static final byte[] NULL = new byte[0];

    @TypeConverter
    public static byte[] serialize(AbstractBean bean) {
        if (bean == null) return NULL;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBufferOutput buffer = KryosKt.byteBuffer(out);
        bean.serialize(buffer);
        IoUtil.flush(buffer);
        IoUtil.close(buffer);
        return out.toByteArray();
    }

    public static <T extends AbstractBean> T deserialize(T bean, byte[] bytes) {
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        ByteBufferInput buffer = KryosKt.byteBuffer(input);
        bean.deserialize(buffer);
        IoUtil.close(buffer);
        return bean;
    }

    @TypeConverter
    public static SOCKSBean socksDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new SOCKSBean(), bytes);
    }

    @TypeConverter
    public static ShadowsocksBean shadowsocksDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new ShadowsocksBean(), bytes);
    }

    @TypeConverter
    public static ShadowsocksRBean shadowsocksRDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new ShadowsocksRBean(), bytes);
    }

    @TypeConverter
    public static VMessBean vmessDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new VMessBean(), bytes);
    }

    @TypeConverter
    public static VLESSBean vlessDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new VLESSBean(), bytes);
    }

}
