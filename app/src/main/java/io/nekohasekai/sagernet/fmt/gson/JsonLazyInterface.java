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

package io.nekohasekai.sagernet.fmt.gson;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import kotlin.Lazy;
import kotlin.LazyKt;

@SuppressWarnings("unchecked")
public abstract class JsonLazyInterface<T> implements Lazy<T> {

    protected JsonElement content;
    protected Gson gson;
    private T value;
    private boolean fromValue;

    public JsonLazyInterface() {
    }

    public JsonLazyInterface(T value) {
        this.value = value;
        this.fromValue = true;
    }

    protected final Lazy<Class<T>> type = LazyKt.lazy(() -> (Class<T>) getType());
    private final Lazy<T> _value = LazyKt.lazy(this::init);

    private T init() {
        if (type.getValue() == null) {
            return null;
        }
        return gson.fromJson(content, type.getValue());
    }

    @Nullable
    protected abstract Class<? extends T> getType();

    @Override
    public T getValue() {
        if (fromValue) return value;
        return _value.getValue();
    }

    @Override
    public boolean isInitialized() {
        if (fromValue) return true;
        return _value.isInitialized();
    }

}
