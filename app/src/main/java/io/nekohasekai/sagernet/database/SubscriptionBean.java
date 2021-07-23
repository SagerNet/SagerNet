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

package io.nekohasekai.sagernet.database;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.nekohasekai.sagernet.SubscriptionType;
import io.nekohasekai.sagernet.fmt.Serializable;
import io.nekohasekai.sagernet.ktx.KryosKt;

public class SubscriptionBean extends Serializable {

    public Integer type;
    public String link;
    public String token;
    public Boolean forceResolve;
    public Boolean deduplication;
    public Boolean forceVMessAEAD;
    public Boolean updateWhenConnectedOnly;
    public String customUserAgent;
    public Boolean autoUpdate;
    public Integer autoUpdateDelay;
    public Integer lastUpdated;

    // SIP008

    public Long bytesUsed;
    public Long bytesRemaining;

    // Open Online Config

    public String username;
    public Integer expiryDate;
    public List<String> protocols;

    public Set<String> selectedGroups;
    public Set<String> selectedTags;

    public SubscriptionBean() {
    }

    @Override
    public void serializeToBuffer(ByteBufferOutput output) {
        output.writeInt(0);

        output.writeInt(type);

        if (type == SubscriptionType.OOCv1) {
            output.writeString(token);
        } else {
            output.writeString(link);
        }

        output.writeBoolean(forceResolve);
        output.writeBoolean(deduplication);
        output.writeBoolean(forceVMessAEAD);
        output.writeBoolean(updateWhenConnectedOnly);
        output.writeString(customUserAgent);
        output.writeBoolean(autoUpdate);
        output.writeInt(autoUpdateDelay);
        output.writeInt(lastUpdated);

        if (type != SubscriptionType.RAW) {
            output.writeLong(bytesUsed);
            output.writeLong(bytesRemaining);
        }

        if (type == SubscriptionType.OOCv1) {
            output.writeString(username);
            output.writeInt(expiryDate);
            KryosKt.writeStringList(output, protocols);
            KryosKt.writeStringList(output, selectedGroups);
            KryosKt.writeStringList(output, selectedTags);
        }

    }

    public void serializeForShare(ByteBufferOutput output) {
        output.writeInt(0);

        output.writeInt(type);

        if (type == SubscriptionType.OOCv1) {
            output.writeString(token);
        } else {
            output.writeString(link);
        }

        output.writeBoolean(forceResolve);
        output.writeBoolean(deduplication);
        output.writeBoolean(forceVMessAEAD);
        output.writeBoolean(updateWhenConnectedOnly);
        output.writeString(customUserAgent);

        if (type != SubscriptionType.RAW) {
            output.writeLong(bytesUsed);
            output.writeLong(bytesRemaining);
        }

        if (type == SubscriptionType.OOCv1) {
            output.writeString(username);
            output.writeInt(expiryDate);
            KryosKt.writeStringList(output, protocols);
        }

    }

    @Override
    public void deserializeFromBuffer(ByteBufferInput input) {
        int version = input.readInt();

        type = input.readInt();
        if (type == SubscriptionType.OOCv1) {
            token = input.readString();
        } else {
            link = input.readString();
        }
        forceResolve = input.readBoolean();
        deduplication = input.readBoolean();
        forceVMessAEAD = input.readBoolean();
        updateWhenConnectedOnly = input.readBoolean();
        customUserAgent = input.readString();
        autoUpdate = input.readBoolean();
        autoUpdateDelay = input.readInt();
        lastUpdated = input.readInt();

        if (type != SubscriptionType.RAW) {
            bytesUsed = input.readLong();
            bytesRemaining = input.readLong();
        }

        if (type == SubscriptionType.OOCv1) {
            username = input.readString();
            expiryDate = input.readInt();
            protocols = KryosKt.readStringList(input);
            if (input.canReadVarInt()) {
                selectedGroups = KryosKt.readStringSet(input);
                selectedTags = KryosKt.readStringSet(input);
            }
        }
    }

    public void deserializeFromShare(ByteBufferInput input) {
        int version = input.readInt();

        type = input.readInt();
        if (type == SubscriptionType.OOCv1) {
            token = input.readString();
        } else {
            link = input.readString();
        }
        forceResolve = input.readBoolean();
        deduplication = input.readBoolean();
        forceVMessAEAD = input.readBoolean();
        updateWhenConnectedOnly = input.readBoolean();
        customUserAgent = input.readString();

        if (type != SubscriptionType.RAW) {
            bytesUsed = input.readLong();
            bytesRemaining = input.readLong();
        }

        if (type == SubscriptionType.OOCv1) {
            username = input.readString();
            expiryDate = input.readInt();
            protocols = KryosKt.readStringList(input);
        }
    }

    @Override
    public void initializeDefaultValues() {
        if (type == null) type = SubscriptionType.RAW;
        if (link == null) link = "";
        if (token == null) token = "";
        if (forceResolve == null) forceResolve = false;
        if (deduplication == null) deduplication = false;
        if (forceVMessAEAD == null) forceVMessAEAD = false;
        if (updateWhenConnectedOnly == null) updateWhenConnectedOnly = false;
        if (customUserAgent == null) customUserAgent = "";
        if (autoUpdate == null) autoUpdate = false;
        if (autoUpdateDelay == null) autoUpdateDelay = 1440;
        if (lastUpdated == null) lastUpdated = 0;

        if (bytesUsed == null) bytesUsed = 0L;
        if (bytesRemaining == null) bytesRemaining = 0L;

        if (username == null) username = "";
        if (expiryDate == null) expiryDate = 0;
        if (protocols == null) protocols = new ArrayList<>();
        if (selectedGroups == null) selectedGroups = new LinkedHashSet<>();
        if (selectedTags == null) selectedTags = new LinkedHashSet<>();

    }

    public static final Creator<SubscriptionBean> CREATOR = new CREATOR<SubscriptionBean>() {
        @NonNull
        @Override
        public SubscriptionBean newInstance() {
            return new SubscriptionBean();
        }

        @Override
        public SubscriptionBean[] newArray(int size) {
            return new SubscriptionBean[size];
        }
    };

}
