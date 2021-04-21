package io.nekohasekai.sagernet.fmt.socks;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class SOCKSBean extends AbstractBean {

    public static SOCKSBean DEFAULT_BEAN = new SOCKSBean() {{
        name = "";
        serverAddress = "127.0.0.1";
        serverPort = 1080;
        username = "";
        password = "";
        udp = false;
    }};

    public String username;
    public String password;
    public boolean udp;

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(username);
        output.writeString(password);
        output.writeBoolean(udp);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        username = input.readString();
        password = input.readString();
        udp = input.readBoolean();
    }

    @NotNull
    @Override
    public SOCKSBean clone() {
        return KryoConverters.deserialize(new SOCKSBean(), KryoConverters.serialize(this));
    }

}
