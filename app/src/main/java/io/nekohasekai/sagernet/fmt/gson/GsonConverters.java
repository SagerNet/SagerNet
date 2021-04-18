package io.nekohasekai.sagernet.fmt.gson;

import androidx.room.TypeConverter;

import java.util.List;

public class GsonConverters {

    @TypeConverter
    public static String toJson(Object value) {
        return GsonsKt.getGson().toJson(value);
    }

    @TypeConverter
    public static List toList(String value) {
        return GsonsKt.getGson().fromJson(value, List.class);
    }

}
