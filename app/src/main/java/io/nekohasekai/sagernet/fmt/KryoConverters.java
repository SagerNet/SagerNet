package io.nekohasekai.sagernet.fmt;

import androidx.room.TypeConverter;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ArrayUtil;
import io.nekohasekai.sagernet.fmt.socks.SOCKSBean;
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
        IoUtil.flush(out);
        IoUtil.close(buffer);
        IoUtil.close(out);
        return out.toByteArray();
    }

    private static <T extends AbstractBean> T deserialize(T bean, byte[] bytes) {
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        ByteBufferInput buffer = KryosKt.byteBuffer(input);
        bean.deserialize(buffer);
        IoUtil.close(buffer);
        IoUtil.close(input);
        return bean;
    }

    @TypeConverter
    public static VMessBean vmessDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new VMessBean(), bytes);
    }

    @TypeConverter
    public static SOCKSBean socksDeserialize(byte[] bytes) {
        if (ArrayUtil.isEmpty(bytes)) return null;
        return deserialize(new SOCKSBean(), bytes);
    }

}
