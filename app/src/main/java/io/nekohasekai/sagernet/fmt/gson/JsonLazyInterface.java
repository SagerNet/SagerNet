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
