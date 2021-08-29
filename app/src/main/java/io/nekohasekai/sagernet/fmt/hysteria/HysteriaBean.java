/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

package io.nekohasekai.sagernet.fmt.hysteria;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class HysteriaBean extends AbstractBean {

    public static final int TYPE_NONE = 0;
    public static final int TYPE_STRING = 1;
    public static final int TYPE_BASE64 = 2;

    public Integer authPayloadType;
    public String authPayload;

    public String obfuscation;
    public String sni;
    public String caText;

    public Integer uploadMbps;
    public Integer downloadMbps;
    public Boolean allowInsecure;
    public Integer streamReceiveWindow;
    public Integer connectionReceiveWindow;
    public Boolean disableMtuDiscovery;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (authPayloadType == null) authPayloadType = TYPE_NONE;
        if (authPayload == null) authPayload = "";
        if (obfuscation == null) obfuscation = "";
        if (sni == null) sni = "";
        if (caText == null) caText = "";

        if (uploadMbps == null) uploadMbps = 10;
        if (downloadMbps == null) downloadMbps = 50;
        if (allowInsecure == null) allowInsecure = false;

        if (streamReceiveWindow == null) streamReceiveWindow = 0;
        if (connectionReceiveWindow == null) connectionReceiveWindow = 0;
        if (disableMtuDiscovery == null) disableMtuDiscovery = false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeInt(authPayloadType);
        output.writeString(authPayload);
        output.writeString(obfuscation);
        output.writeString(sni);

        output.writeInt(uploadMbps);
        output.writeInt(downloadMbps);
        output.writeBoolean(allowInsecure);

        output.writeString(caText);
        output.writeInt(streamReceiveWindow);
        output.writeInt(connectionReceiveWindow);
        output.writeBoolean(disableMtuDiscovery);

    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        authPayloadType = input.readInt();
        authPayload = input.readString();
        obfuscation = input.readString();
        sni = input.readString();
        uploadMbps = input.readInt();
        downloadMbps = input.readInt();
        allowInsecure = input.readBoolean();
        if (version >= 1) {
            caText = input.readString();
            streamReceiveWindow = input.readInt();
            connectionReceiveWindow = input.readInt();
            disableMtuDiscovery = input.readBoolean();
        }
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof HysteriaBean)) return;
        HysteriaBean bean = ((HysteriaBean) other);
        bean.uploadMbps = uploadMbps;
        bean.downloadMbps = downloadMbps;
        bean.allowInsecure = allowInsecure;
        bean.disableMtuDiscovery = disableMtuDiscovery;
    }

    @NotNull
    @Override
    public HysteriaBean clone() {
        return KryoConverters.deserialize(new HysteriaBean(), KryoConverters.serialize(this));
    }

    public static final Creator<HysteriaBean> CREATOR = new CREATOR<HysteriaBean>() {
        @NonNull
        @Override
        public HysteriaBean newInstance() {
            return new HysteriaBean();
        }

        @Override
        public HysteriaBean[] newArray(int size) {
            return new HysteriaBean[size];
        }
    };
}
