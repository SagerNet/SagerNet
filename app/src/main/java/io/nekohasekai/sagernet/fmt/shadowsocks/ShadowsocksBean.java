package io.nekohasekai.sagernet.fmt.shadowsocks;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class ShadowsocksBean extends AbstractBean {

    public static ShadowsocksBean DEFAULT_BEAN = new ShadowsocksBean() {{
        name = "";
        serverAddress = "127.0.0.1";
        serverPort = 1080;
        method = "aes-256-gcm";
        password = "";
        plugin = "";
    }};

    public String method;
    public String password;
    public String plugin;

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(method);
        output.writeString(password);
        output.writeString(plugin);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        method = input.readString();
        password = input.readString();
        plugin = input.readString();
    }

    @NotNull
    @Override
    public ShadowsocksBean clone() {
        return KryoConverters.shadowsocksDeserialize(KryoConverters.serialize(this));
    }
}
