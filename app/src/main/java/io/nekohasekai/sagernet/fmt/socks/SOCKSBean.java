package io.nekohasekai.sagernet.fmt.socks;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.nekohasekai.sagernet.fmt.AbstractBean;

public class SOCKSBean extends AbstractBean {

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
}
