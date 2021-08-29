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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * LFU(least frequently used) 最少使用率缓存<br>
 * 根据使用次数来判定对象是否被持续缓存<br>
 * 使用率是通过访问次数计算的。<br>
 * 当缓存满时清理过期对象。<br>
 * 清理后依旧满的情况下清除最少访问（访问计数最小）的对象并将其他对象的访问数减去这个最小访问数，以便新对象进入后可以公平计数。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author Looly, jodd
 */
public class LFUCacheWithoutLock<K, V> extends AbstractCacheWithoutLock<K, V> {
    private static final long serialVersionUID = 1L;

    /**
     * 构造
     *
     * @param capacity 容量
     */
    public LFUCacheWithoutLock(int capacity) {
        this(capacity, 0);
    }

    /**
     * 构造
     *
     * @param capacity 容量
     * @param timeout  过期时长
     */
    public LFUCacheWithoutLock(int capacity, long timeout) {
        if (Integer.MAX_VALUE == capacity) {
            capacity -= 1;
        }

        this.capacity = capacity;
        this.timeout = timeout;
        cacheMap = createCacheMap();
    }

    protected Map<K, CacheObj<K, V>> createCacheMap() {
        return new HashMap<>(capacity + 1, 1.0f);
    }

    // ---------------------------------------------------------------- prune

    /**
     * 清理过期对象。<br>
     * 清理后依旧满的情况下清除最少访问（访问计数最小）的对象并将其他对象的访问数减去这个最小访问数，以便新对象进入后可以公平计数。
     *
     * @return 清理个数
     */
    @Override
    protected int pruneCache() {
        int count = 0;
        CacheObj<K, V> comin = null;

        // 清理过期对象并找出访问最少的对象
        Iterator<CacheObj<K, V>> values = cacheMap.values().iterator();
        CacheObj<K, V> co;
        while (values.hasNext()) {
            co = values.next();
            if (co.isExpired() == true) {
                values.remove();
                onRemove(co.key, co.obj);
                count++;
                continue;
            }

            //找出访问最少的对象
            if (comin == null || co.accessCount.get() < comin.accessCount.get()) {
                comin = co;
            }
        }

        // 减少所有对象访问量，并清除减少后为0的访问对象
        if (isFull() && comin != null) {
            long minAccessCount = comin.accessCount.get();

            values = cacheMap.values().iterator();
            CacheObj<K, V> co1;
            while (values.hasNext()) {
                co1 = values.next();
                if (co1.accessCount.addAndGet(-minAccessCount) <= 0) {
                    values.remove();
                    onRemove(co1.key, co1.obj);
                    count++;
                }
            }
        }

        return count;
    }
}
