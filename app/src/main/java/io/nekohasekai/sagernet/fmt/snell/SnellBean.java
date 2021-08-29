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

package io.nekohasekai.sagernet.fmt.snell;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class SnellBean extends AbstractBean {

    public Integer version;
    public String psk;
    public String obfsMode;
    public String obfsHost;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (version == null) version = 2;
        if (psk == null) psk = "";
        if (obfsMode == null) obfsMode = "http";
        if (obfsHost == null) obfsHost = "bing.com";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeInt(version);
        output.writeString(psk);
        output.writeString(obfsMode);
        output.writeString(obfsHost);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int serVer = input.readInt();
        super.deserialize(input);
        version = input.readInt();
        psk = input.readString();
        obfsMode = input.readString();
        obfsHost = input.readString();
    }

    @NotNull
    @Override
    public SnellBean clone() {
        return KryoConverters.deserialize(new SnellBean(), KryoConverters.serialize(this));
    }

    public static final Creator<SnellBean> CREATOR = new CREATOR<SnellBean>() {
        @NonNull
        @Override
        public SnellBean newInstance() {
            return new SnellBean();
        }

        @Override
        public SnellBean[] newArray(int size) {
            return new SnellBean[size];
        }
    };
}
