package io.nekohasekai.sagernet.fmt.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

public class JsonLazyFactory implements TypeAdapterFactory {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!JsonLazyInterface.class.isAssignableFrom(type.getRawType())) return null;
        return new JsonLazyAdapter(gson, type.getRawType());
    }

}
