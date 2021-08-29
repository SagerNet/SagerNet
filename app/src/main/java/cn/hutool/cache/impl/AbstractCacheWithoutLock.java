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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheListener;
import cn.hutool.core.collection.CopiedIter;
import cn.hutool.core.lang.func.Func0;

/**
 * 超时和限制大小的缓存的默认实现<br>
 * 继承此抽象缓存需要：<br>
 * <ul>
 * <li>创建一个新的Map</li>
 * <li>实现 {@code prune} 策略</li>
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author Looly, jodd
 */
public abstract class AbstractCacheWithoutLock<K, V> implements Cache<K, V> {
    private static final long serialVersionUID = 1L;

    protected Map<K, CacheObj<K, V>> cacheMap;

    /**
     * 写的时候每个key一把锁，降低锁的粒度
     */
    protected final Map<K, Lock> keyLockMap = new ConcurrentHashMap<>();

    /**
     * 返回缓存容量，{@code 0}表示无大小限制
     */
    protected int capacity;
    /**
     * 缓存失效时长， {@code 0} 表示无限制，单位毫秒
     */
    protected long timeout;

    /**
     * 每个对象是否有单独的失效时长，用于决定清理过期对象是否有必要。
     */
    protected boolean existCustomTimeout;

    /**
     * 命中数，即命中缓存计数
     */
    protected AtomicLong hitCount = new AtomicLong();
    /**
     * 丢失数，即未命中缓存计数
     */
    protected AtomicLong missCount = new AtomicLong();

    /**
     * 缓存监听
     */
    protected CacheListener<K, V> listener;

    // ---------------------------------------------------------------- put start
    @Override
    public void put(K key, V object) {
        put(key, object, timeout);
    }

    @Override
    public void put(K key, V object, long timeout) {
        putWithoutLock(key, object, timeout);
    }

    /**
     * 加入元素，无锁
     *
     * @param key     键
     * @param object  值
     * @param timeout 超时时长
     * @since 4.5.16
     */
    public void putWithoutLock(K key, V object, long timeout) {
        CacheObj<K, V> co = new CacheObj<>(key, object, timeout);
        if (timeout != 0) {
            existCustomTimeout = true;
        }
        if (isFull()) {
            pruneCache();
        }
        cacheMap.put(key, co);
    }
    // ---------------------------------------------------------------- put end

    // ---------------------------------------------------------------- get start
    @Override
    public boolean containsKey(K key) {
        // 不存在或已移除
        final CacheObj<K, V> co = cacheMap.get(key);
        if (co == null) {
            return false;
        }

        if (false == co.isExpired()) {
            // 命中
            return true;
        }

        // 过期
        remove(key, true);
        return false;
    }

    /**
     * @return 命中数
     */
    public long getHitCount() {
        return hitCount.get();
    }

    /**
     * @return 丢失数
     */
    public long getMissCount() {
        return missCount.get();
    }

    @Override
    public V get(K key, boolean isUpdateLastAccess, Func0<V> supplier) {
        V v = get(key, isUpdateLastAccess);
        if (null == v && null != supplier) {
            //每个key单独获取一把锁，降低锁的粒度提高并发能力，see pr#1385@Github
            final Lock keyLock = keyLockMap.computeIfAbsent(key, k -> new ReentrantLock());
            keyLock.lock();
            try {
                // 双重检查锁，防止在竞争锁的过程中已经有其它线程写入
                final CacheObj<K, V> co = cacheMap.get(key);
                if (null == co || co.isExpired()) {
                    try {
                        v = supplier.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    put(key, v, this.timeout);
                } else {
                    v = co.get(isUpdateLastAccess);
                }
            } finally {
                keyLock.unlock();
                keyLockMap.remove(key);
            }
        }
        return v;
    }

    @Override
    public V get(K key, boolean isUpdateLastAccess) {
        // 尝试读取缓存，使用乐观读锁
        CacheObj<K, V> co = cacheMap.get(key);

        // 未命中
        if (null == co) {
            missCount.incrementAndGet();
            return null;
        } else if (false == co.isExpired()) {
            hitCount.incrementAndGet();
            return co.get(isUpdateLastAccess);
        }

        // 过期，既不算命中也不算非命中
        remove(key, true);
        return null;
    }

    // ---------------------------------------------------------------- get end

    @Override
    public Iterator<V> iterator() {
        CacheObjIterator<K, V> copiedIterator = (CacheObjIterator<K, V>) this.cacheObjIterator();
        return new CacheValuesIterator<>(copiedIterator);
    }

    @Override
    public Iterator<CacheObj<K, V>> cacheObjIterator() {
        CopiedIter<CacheObj<K, V>> copiedIterator;
        copiedIterator = CopiedIter.copyOf(this.cacheMap.values().iterator());
        return new CacheObjIterator<>(copiedIterator);
    }

    // ---------------------------------------------------------------- prune start

    /**
     * 清理实现<br>
     * 子类实现此方法时无需加锁
     *
     * @return 清理数
     */
    protected abstract int pruneCache();

    @Override
    public final int prune() {
        return pruneCache();
    }
    // ---------------------------------------------------------------- prune end

    // ---------------------------------------------------------------- common start
    @Override
    public int capacity() {
        return capacity;
    }

    /**
     * @return 默认缓存失效时长。<br>
     * 每个对象可以单独设置失效时长
     */
    @Override
    public long timeout() {
        return timeout;
    }

    /**
     * 只有设置公共缓存失效时长或每个对象单独的失效时长时清理可用
     *
     * @return 过期对象清理是否可用，内部使用
     */
    protected boolean isPruneExpiredActive() {
        return (timeout != 0) || existCustomTimeout;
    }

    @Override
    public boolean isFull() {
        return (capacity > 0) && (cacheMap.size() >= capacity);
    }

    @Override
    public void remove(K key) {
        remove(key, false);
    }

    @Override
    public void clear() {
        cacheMap.clear();
    }

    @Override
    public int size() {
        return cacheMap.size();
    }

    @Override
    public boolean isEmpty() {
        return cacheMap.isEmpty();
    }

    @Override
    public String toString() {
        return this.cacheMap.toString();
    }
    // ---------------------------------------------------------------- common end

    /**
     * 设置监听
     *
     * @param listener 监听
     * @return this
     * @since 5.5.2
     */
    @Override
    public AbstractCacheWithoutLock<K, V> setListener(CacheListener<K, V> listener) {
        this.listener = listener;
        return this;
    }

    /**
     * 返回所有键
     *
     * @return 所有键
     * @since 5.5.9
     */
    public Set<K> keySet() {
        return this.cacheMap.keySet();
    }

    /**
     * 对象移除回调。默认无动作<br>
     * 子类可重写此方法用于监听移除事件，如果重写，listener将无效
     *
     * @param key          键
     * @param cachedObject 被缓存的对象
     */
    protected void onRemove(K key, V cachedObject) {
        final CacheListener<K, V> listener = this.listener;
        if (null != listener) {
            listener.onRemove(key, cachedObject);
        }
    }

    /**
     * 移除key对应的对象
     *
     * @param key           键
     * @param withMissCount 是否计数丢失数
     */
    private void remove(K key, boolean withMissCount) {
        CacheObj<K, V> co = removeWithoutLock(key, withMissCount);
        if (null != co) {
            onRemove(co.key, co.obj);
        }
    }

    /**
     * 移除key对应的对象，不加锁
     *
     * @param key           键
     * @param withMissCount 是否计数丢失数
     * @return 移除的对象，无返回null
     */
    private CacheObj<K, V> removeWithoutLock(K key, boolean withMissCount) {
        final CacheObj<K, V> co = cacheMap.remove(key);
        if (withMissCount) {
            // 在丢失计数有效的情况下，移除一般为get时的超时操作，此处应该丢失数+1
            this.missCount.incrementAndGet();
        }
        return co;
    }
}
