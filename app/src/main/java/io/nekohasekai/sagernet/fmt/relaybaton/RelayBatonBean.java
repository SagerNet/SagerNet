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

package io.nekohasekai.sagernet.fmt.relaybaton;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.brook.BrookBean;

public class RelayBatonBean extends AbstractBean {

    public String username;
    public String password;

    @Override
    public void initializeDefaultValues() {
        if (serverPort == 0) serverPort = 443;
        super.initializeDefaultValues();
        if (username == null) username = "";
        if (password == null) password = "";
    }

    @Override
    public boolean canMapping() {
        return false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        output.writeString(serverAddress);
        output.writeString(username);
        output.writeString(password);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        serverAddress = input.readString();
        username = input.readString();
        password = input.readString();
    }

    @NotNull
    @Override
    public RelayBatonBean clone() {
        return KryoConverters.deserialize(new RelayBatonBean(), KryoConverters.serialize(this));
    }

    public static final Creator<RelayBatonBean> CREATOR = new CREATOR<RelayBatonBean>() {
        @NonNull
        @Override
        public RelayBatonBean newInstance() {
            return new RelayBatonBean();
        }

        @Override
        public RelayBatonBean[] newArray(int size) {
            return new RelayBatonBean[size];
        }
    };
}
