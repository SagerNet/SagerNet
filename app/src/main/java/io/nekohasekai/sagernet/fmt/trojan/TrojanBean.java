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

package io.nekohasekai.sagernet.fmt.trojan;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import cn.hutool.core.util.StrUtil;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.brook.BrookBean;

public class TrojanBean extends AbstractBean {

    public String password;

    public String security;
    public String sni;
    public String alpn;

    // --------------------------------------- //

    public Boolean allowInsecure;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        if (password == null) password = "";
        if (StrUtil.isBlank(security)) security = "tls";
        if (sni == null) sni = "";
        if (alpn == null) alpn = "";
        if (allowInsecure == null) allowInsecure = false;

    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(password);
        output.writeString(security);
        output.writeString(sni);
        output.writeString(alpn);
        output.writeBoolean(allowInsecure);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        password = input.readString();
        security = input.readString();
        sni = input.readString();
        alpn = input.readString();
        if (version >= 1) {
            allowInsecure = input.readBoolean();
        }
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof TrojanBean)) return;
        TrojanBean bean = ((TrojanBean) other);
        bean.allowInsecure = allowInsecure;
    }

    @NotNull
    @Override
    public TrojanBean clone() {
        return KryoConverters.deserialize(new TrojanBean(), KryoConverters.serialize(this));
    }

    public static final Creator<TrojanBean> CREATOR = new CREATOR<TrojanBean>() {
        @NonNull
        @Override
        public TrojanBean newInstance() {
            return new TrojanBean();
        }

        @Override
        public TrojanBean[] newArray(int size) {
            return new TrojanBean[size];
        }
    };
}
