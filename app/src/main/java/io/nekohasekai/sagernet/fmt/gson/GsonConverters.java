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

import androidx.room.TypeConverter;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import cn.hutool.core.util.StrUtil;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;

public class GsonConverters {

    @TypeConverter
    public static String toJson(Object value) {
        if (value instanceof Collection) {
            if (((Collection<?>) value).isEmpty()) return "";
        }
        return GsonsKt.getGson().toJson(value);
    }

    @TypeConverter
    public static List toList(String value) {
        if (StrUtil.isBlank(value)) return CollectionsKt.listOf();
        return GsonsKt.getGson().fromJson(value, List.class);
    }

    @TypeConverter
    public static Set toSet(String value) {
        if (StrUtil.isBlank(value)) return SetsKt.setOf();
        return GsonsKt.getGson().fromJson(value, Set.class);
    }

}
