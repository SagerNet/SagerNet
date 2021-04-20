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
