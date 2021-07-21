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

package io.nekohasekai.sagernet.fmt.brook;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import io.nekohasekai.sagernet.database.SubscriptionBean;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class BrookBean extends AbstractBean {

    public String protocol;
    public String password;
    public String wsPath;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (protocol == null) protocol = "";
        if (password == null) password = "";
        if (wsPath == null) wsPath = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(protocol);
        output.writeString(password);
        switch (protocol) {
            case "ws":
            case "wss": {
                output.writeString(wsPath);
                break;
            }
        }
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        protocol = input.readString();
        password = input.readString();
        if (version > 0) switch (protocol) {
            case "ws":
            case "wss": {
                wsPath = input.readString();
                break;
            }
        }
    }

    @NonNull
    @Override
    public BrookBean clone() {
        return KryoConverters.deserialize(new BrookBean(), KryoConverters.serialize(this));
    }

    public static final Creator<BrookBean> CREATOR = new CREATOR<BrookBean>() {
        @NonNull
        @Override
        public BrookBean newInstance() {
            return new BrookBean();
        }

        @Override
        public BrookBean[] newArray(int size) {
            return new BrookBean[size];
        }
    };


}
