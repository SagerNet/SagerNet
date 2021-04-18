package io.nekohasekai.sagernet.fmt;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

public class AbstractBean {

    public String serverAddress;
    public int serverPort;

    public String name;

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

}
