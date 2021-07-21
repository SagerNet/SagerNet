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
import io.nekohasekai.sagernet.database.SubscriptionBean;
import io.nekohasekai.sagernet.fmt.brook.BrookBean;
import io.nekohasekai.sagernet.fmt.http.HttpBean;
import io.nekohasekai.sagernet.fmt.internal.BalancerBean;
import io.nekohasekai.sagernet.fmt.internal.ChainBean;
import io.nekohasekai.sagernet.fmt.internal.ConfigBean;
import io.nekohasekai.sagernet.fmt.naive.NaiveBean;
import io.nekohasekai.sagernet.fmt.pingtunnel.PingTunnelBean;
import io.nekohasekai.sagernet.fmt.relaybaton.RelayBatonBean;
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean;
import io.nekohasekai.sagernet.fmt.shadowsocksr.ShadowsocksRBean;
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean;
import io.nekohasekai.sagernet.fmt.trojan.TrojanBean;
import io.nekohasekai.sagernet.fmt.trojan_go.TrojanGoBean;
import io.nekohasekai.sagernet.fmt.v2ray.VLESSBean;
import io.nekohasekai.sagernet.fmt.v2ray.VMessBean;
import io.nekohasekai.sagernet.ktx.KryosKt;

public class KryoConverters {

    private static final byte[] NULL = new byte[0];

    @TypeConverter
    public static byte[] serialize(Serializable bean) {
        if (bean == null) return NULL;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBufferOutput buffer = KryosKt.byteBuffer(out);
        bean.serializeToBuffer(buffer);
        IoUtil.flush(buffer);
        IoUtil.close(buffer);
        return out.toByteArray();
    }

    public static <T extends Serializable> T deserialize(T bean, byte[] bytes) {
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        ByteBufferInput buffer = KryosKt.byteBuffer(input);
        bean.deserializeFromBuffer(buffer);
        IoUtil.close(buffer);
        bean.initializeDefaultValues();
        return bean;
    }

    public static byte[] serializeWithoutName(AbstractBean bean) {
        if (bean == null) return NULL;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBufferOutput buffer = KryosKt.byteBuffer(out);
        bean.serialize(buffer);
        IoUtil.flush(buffer);
        IoUtil.close(buffer);
        return out.toByteArray();
    }

    public static <T extends AbstractBean> T deserializeWithoutName(T bean, byte[] bytes) {
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
    public static HttpBean httpDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new HttpBean(), bytes);
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

    @TypeConverter
    public static TrojanBean trojanDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new TrojanBean(), bytes);
    }

    @TypeConverter
    public static TrojanGoBean trojanGoDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new TrojanGoBean(), bytes);
    }

    @TypeConverter
    public static NaiveBean naiveDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new NaiveBean(), bytes);
    }

    @TypeConverter
    public static PingTunnelBean pingTunnelDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new PingTunnelBean(), bytes);
    }

    @TypeConverter
    public static RelayBatonBean relayBatonDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new RelayBatonBean(), bytes);
    }

    @TypeConverter
    public static BrookBean brookDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new BrookBean(), bytes);
    }

    @TypeConverter
    public static ConfigBean configDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new ConfigBean(), bytes);
    }

    @TypeConverter
    public static ChainBean chainDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new ChainBean(), bytes);
    }

    @TypeConverter
    public static BalancerBean balancerBeanDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new BalancerBean(), bytes);
    }

    @TypeConverter
    public static SubscriptionBean subscriptionDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new SubscriptionBean(), bytes);
    }

}
