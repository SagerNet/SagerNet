package io.nekohasekai.sagernet.fmt.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class JsonOrAdapter<X, Y> extends TypeAdapter<JsonOr<X, Y>> {

    private final Gson gson;
    private final TypeToken<X> typeX;
    private final TypeToken<Y> typeY;
    private final JsonToken tokenX;
    private final JsonToken tokenY;

    public JsonOrAdapter(Gson gson, TypeToken<X> typeX, TypeToken<Y> typeY, JsonToken tokenX, JsonToken tokenY) {
        this.gson = gson;
        this.typeX = typeX;
        this.typeY = typeY;
        this.tokenX = tokenX;
        this.tokenY = tokenY;
    }

    @Override
    public void write(JsonWriter out, JsonOr<X, Y> value) throws IOException {
        if (value.valueX != null) {
            gson.getAdapter(typeX).write(out, value.valueX);
        } else {
            gson.getAdapter(typeY).write(out, value.valueY);
        }
    }

    @Override
    public JsonOr<X, Y> read(JsonReader in) throws IOException {
        if (in.peek() == tokenX) {
            return new JsonOr<>(gson.getAdapter(typeX).read(in), null);
        } else {
            return new JsonOr<>(null, gson.getAdapter(typeY).read(in));
        }

    }
}
