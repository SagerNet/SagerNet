package io.nekohasekai.sagernet.fmt.v2ray;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import cn.hutool.core.util.StrUtil;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class VMessBean extends AbstractBean {

    public String uuid;
    public String path;

    public String tag;
    public boolean tls;
    public String network;
    public int kcpUpLinkCapacity;
    public int kcpDownLinkCapacity;
    public String header;
    public int mux;

    // custom

    public String requestHost;
    public String sni;
    public String security;
    public int alterId;

    protected void initDefaultValues() {
        if (StrUtil.isBlank(network)) {
            network = "tls";
        }
        if (StrUtil.isBlank(security)) {
            security = "auto";
        }
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(uuid);
        output.writeString(tag);
        output.writeBoolean(tls);
        output.writeString(network);
        output.writeInt(kcpUpLinkCapacity);
        output.writeInt(kcpDownLinkCapacity);
        output.writeString(header);
        output.writeInt(mux);

        // custom
        output.writeString(requestHost);
        output.writeString(sni);
        output.writeString(security);
        output.writeInt(alterId);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        uuid = input.readString();
        tag = input.readString();
        tls = input.readBoolean();
        network = input.readString();
        kcpUpLinkCapacity = input.readInt();
        kcpDownLinkCapacity = input.readInt();
        header = input.readString();
        mux = input.readInt();

        // custom
        requestHost = input.readString();
        sni = input.readString();
        security = input.readString();
        alterId = input.readInt();
    }

    @NotNull
    @Override
    public VMessBean clone() {
        return KryoConverters.deserialize(new VMessBean(), KryoConverters.serialize(this));
    }
}
