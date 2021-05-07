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

package io.nekohasekai.sagernet.fmt.gson;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class JsonLazyAdapter<T> extends TypeAdapter<JsonLazyInterface<T>> {

    private final Gson gson;
    private final Class<JsonLazyInterface<T>> clazz;

    public JsonLazyAdapter(Gson gson, Class<JsonLazyInterface<T>> clazz) {
        this.gson = gson;
        this.clazz = clazz;
    }

    @Override
    public void write(JsonWriter out, JsonLazyInterface<T> value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            gson.getAdapter(value.type.getValue()).write(out, value.getValue());
        }
    }

    @Override
    public JsonLazyInterface<T> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        try {
            JsonLazyInterface<T> instance = clazz.newInstance();
            instance.gson = gson;
            instance.content = gson.getAdapter(JsonElement.class).read(in);
            return instance;
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw ((IOException) e);
            } else {
                throw new IOException(e);
            }
        }
    }
}
