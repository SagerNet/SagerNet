package io.nekohasekai.sagernet.fmt.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class JsonOrAdapterFactory implements TypeAdapterFactory {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!JsonOr.class.isAssignableFrom(type.getRawType())) return null;
        Type superclass = type.getRawType().getGenericSuperclass();
        if (superclass instanceof Class) {
            throw new RuntimeException("Missing type parameter.");
        }
        ParameterizedType parameterized = (ParameterizedType) superclass;
        Type[] args = parameterized.getActualTypeArguments();
        try {
            JsonOr<?, ?> instance = (JsonOr<?, ?>) type.getRawType().newInstance();
            return new JsonOrAdapter(gson, TypeToken.get(args[0]), TypeToken.get(args[1]), instance.tokenX, instance.tokenY);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

}
