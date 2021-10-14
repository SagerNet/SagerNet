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

package io.nekohasekai.sagernet.fmt;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.hutool.core.clone.Cloneable;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import io.nekohasekai.sagernet.ExtraType;
import io.nekohasekai.sagernet.fmt.gson.GsonsKt;
import io.nekohasekai.sagernet.ktx.KryosKt;
import io.nekohasekai.sagernet.ktx.NetsKt;

public abstract class AbstractBean extends Serializable implements Cloneable<AbstractBean> {

    public String serverAddress;
    public Integer serverPort;
    public String name;

    public transient boolean isChain;
    public transient String finalAddress;
    public transient int finalPort;

    public int extraType;
    public String profileId;
    public String group;
    public String owner;
    public List<String> tags;

    public String displayName() {
        if (StrUtil.isNotBlank(name)) {
            return name;
        } else {
            return serverAddress + ":" + serverPort;
        }
    }

    public String displayAddress() {
        return serverAddress + ":" + serverPort;
    }

    public String network() {
        return "tcp,udp";
    }

    public boolean canICMPing() {
        return true;
    }

    public boolean canTCPing() {
        return true;
    }

    public boolean canMapping() {
        return true;
    }

    @Override
    public void initializeDefaultValues() {
        if (StrUtil.isBlank(serverAddress)) {
            serverAddress = "127.0.0.1";
        } else if (serverAddress.startsWith("[") && serverAddress.endsWith("]")) {
            serverAddress = NetsKt.unwrapHost(serverAddress);
        }
        if (serverPort == null) {
            serverPort = 1080;
        }
        if (name == null) name = "";

        finalAddress = serverAddress;
        finalPort = serverPort;

        if (profileId == null) profileId = "";
        if (group == null) group = "";
        if (tags == null) tags = new ArrayList<>();
    }


    private transient boolean serializeWithoutName;

    @Override
    public void serializeToBuffer(@NonNull ByteBufferOutput output) {
        serialize(output);

        output.writeInt(1);
        if (!serializeWithoutName) {
            output.writeString(name);
        }
        output.writeInt(extraType);
        if (extraType == ExtraType.NONE) return;
        output.writeString(profileId);
        if (extraType == ExtraType.OOCv1) {
            output.writeString(group);
            output.writeString(owner);
            KryosKt.writeStringList(output, tags);
        }
    }

    @Override
    public void deserializeFromBuffer(@NonNull ByteBufferInput input) {
        deserialize(input);

        int extraVersion = input.readInt();

        name = input.readString();
        extraType = input.readInt();
        if (extraType == ExtraType.NONE) return;
        profileId = input.readString();

        if (extraType == ExtraType.OOCv1) {
            group = input.readString();
            if (extraVersion >= 1) {
                owner = input.readString();
            }
            tags = KryosKt.readStringList(input);
        }
    }

    public void serialize(ByteBufferOutput output) {
        output.writeString(serverAddress);
        output.writeInt(serverPort);
    }

    public void deserialize(ByteBufferInput input) {
        serverAddress = input.readString();
        serverPort = input.readInt();
    }

    @NotNull
    @Override
    public abstract AbstractBean clone();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        try {
            serializeWithoutName = true;
            ((AbstractBean) o).serializeWithoutName = true;
            return Arrays.equals(KryoConverters.serialize(this), KryoConverters.serialize((AbstractBean) o));
        } finally {
            serializeWithoutName = false;
            ((AbstractBean) o).serializeWithoutName = false;
        }
    }

    @Override
    public int hashCode() {
        try {
            serializeWithoutName = true;
            return Arrays.hashCode(KryoConverters.serialize(this));
        } finally {
            serializeWithoutName = false;
        }
    }

    @NotNull
    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + JSONUtil.formatJsonStr(GsonsKt.getGson().toJson(this));
    }

    public void applyFeatureSettings(AbstractBean other) {
    }

}
