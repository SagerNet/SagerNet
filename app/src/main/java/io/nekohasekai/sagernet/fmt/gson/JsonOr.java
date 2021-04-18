package io.nekohasekai.sagernet.fmt.gson;

import androidx.annotation.NonNull;

import com.google.gson.stream.JsonToken;

public class JsonOr<X, Y> {

    public JsonToken tokenX;
    public JsonToken tokenY;

    public X valueX;
    public Y valueY;

    public JsonOr(JsonToken tokenX, JsonToken tokenY) {
        this.tokenX = tokenX;
        this.tokenY = tokenY;
    }

    protected JsonOr(X valueX, Y valueY) {
        this.valueX = valueX;
        this.valueY = valueY;
    }

    @NonNull
    @Override
    public String toString() {
        return valueX != null ? valueX.toString() : valueY != null ? valueY.toString() : "null";
    }
}
