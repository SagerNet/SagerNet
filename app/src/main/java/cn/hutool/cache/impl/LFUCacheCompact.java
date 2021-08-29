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


package cn.hutool.cache.impl;

import android.os.Build;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.hutool.cache.Cache;

public class LFUCacheCompact<K, V> {

    protected int capacity;
    protected long timeout;

    public LFUCacheCompact(int capacity, long timeout) {
        if (Integer.MAX_VALUE == capacity) {
            capacity -= 1;
        }

        this.capacity = capacity;
        this.timeout = timeout;
    }

    protected void onRemove(K key, V cachedObject) {
    }

    public Cache<K, V> build(boolean async) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new LFUCache<K, V>(capacity, timeout) {
                {
                    if (async) {
                        cacheMap = new ConcurrentHashMap<>();
                    }
                }

                @Override
                protected void onRemove(K key, V cachedObject) {
                    LFUCacheCompact.this.onRemove(key, cachedObject);
                }
            };
        } else {
            return new LFUCacheWithoutLock<K, V>(capacity, timeout) {
                @Override
                protected Map<K, CacheObj<K, V>> createCacheMap() {
                    return new ConcurrentHashMap<>();
                }

                @Override
                protected void onRemove(K key, V cachedObject) {
                    LFUCacheCompact.this.onRemove(key, cachedObject);
                }
            };
        }
    }

}
