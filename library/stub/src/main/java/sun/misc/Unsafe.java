/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.misc;

import java.lang.reflect.Field;

/**
 * A collection of methods for performing low-level, unsafe operations.
 * Although the class and all methods are public, use of this class is
 * limited because only trusted code can obtain instances of it.
 *
 * @author John R. Rose
 * @see #getUnsafe
 */
public final class Unsafe {
    /**
     * Traditional dalvik name.
     */
    private static final Unsafe THE_ONE = new Unsafe();
    private static final Unsafe theUnsafe = THE_ONE;
    public static final int INVALID_FIELD_OFFSET = -1;

    /**
     * This class is only privately instantiable.
     */
    private Unsafe() {
    }

    /**
     * Gets the unique instance of this class. This is only allowed in
     * very limited situations.
     */
    public static Unsafe getUnsafe() {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the raw byte offset from the start of an object's memory to
     * the memory used to store the indicated instance field.
     *
     * @param field non-{@code null}; the field in question, which must be an
     *              instance field
     * @return the offset to the field
     */
    public long objectFieldOffset(Field field) {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the offset from the start of an array object's memory to
     * the memory used to store its initial (zeroeth) element.
     *
     * @param clazz non-{@code null}; class in question; must be an array class
     * @return the offset to the initial element
     */
    public int arrayBaseOffset(Class clazz) {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the size of each element of the given array class.
     *
     * @param clazz non-{@code null}; class in question; must be an array class
     * @return &gt; 0; the size of each element of the array
     */
    public int arrayIndexScale(Class clazz) {
        throw new UnsupportedOperationException();
    }

    /**
     * Performs a compare-and-set operation on an {@code int}
     * field within the given object.
     *
     * @param obj           non-{@code null}; object containing the field
     * @param offset        offset to the field within {@code obj}
     * @param expectedValue expected value of the field
     * @param newValue      new value to store in the field if the contents are
     *                      as expected
     * @return {@code true} if the new value was in fact stored, and
     * {@code false} if not
     */
    public native boolean compareAndSwapInt(Object obj, long offset,
                                            int expectedValue, int newValue);

    /**
     * Performs a compare-and-set operation on a {@code long}
     * field within the given object.
     *
     * @param obj           non-{@code null}; object containing the field
     * @param offset        offset to the field within {@code obj}
     * @param expectedValue expected value of the field
     * @param newValue      new value to store in the field if the contents are
     *                      as expected
     * @return {@code true} if the new value was in fact stored, and
     * {@code false} if not
     */
    public native boolean compareAndSwapLong(Object obj, long offset,
                                             long expectedValue, long newValue);

    /**
     * Performs a compare-and-set operation on an {@code obj}
     * field (that is, a reference field) within the given object.
     *
     * @param obj           non-{@code null}; object containing the field
     * @param offset        offset to the field within {@code obj}
     * @param expectedValue expected value of the field
     * @param newValue      new value to store in the field if the contents are
     *                      as expected
     * @return {@code true} if the new value was in fact stored, and
     * {@code false} if not
     */
    public native boolean compareAndSwapObject(Object obj, long offset,
                                               Object expectedValue, Object newValue);

    /**
     * Gets an {@code int} field from the given object,
     * using {@code volatile} semantics.
     *
     * @param obj    non-{@code null}; object containing the field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native int getIntVolatile(Object obj, long offset);

    /**
     * Stores an {@code int} field into the given object,
     * using {@code volatile} semantics.
     *
     * @param obj      non-{@code null}; object containing the field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putIntVolatile(Object obj, long offset, int newValue);

    /**
     * Gets a {@code long} field from the given object,
     * using {@code volatile} semantics.
     *
     * @param obj    non-{@code null}; object containing the field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native long getLongVolatile(Object obj, long offset);

    /**
     * Stores a {@code long} field into the given object,
     * using {@code volatile} semantics.
     *
     * @param obj      non-{@code null}; object containing the field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putLongVolatile(Object obj, long offset, long newValue);

    /**
     * Gets an {@code obj} field from the given object,
     * using {@code volatile} semantics.
     *
     * @param obj    non-{@code null}; object containing the field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native Object getObjectVolatile(Object obj, long offset);

    /**
     * Stores an {@code obj} field into the given object,
     * using {@code volatile} semantics.
     *
     * @param obj      non-{@code null}; object containing the field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putObjectVolatile(Object obj, long offset,
                                         Object newValue);

    /**
     * Gets an {@code int} field from the given object.
     *
     * @param obj    non-{@code null}; object containing int field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native int getInt(Object obj, long offset);

    /**
     * Stores an {@code int} field into the given object.
     *
     * @param obj      non-{@code null}; object containing int field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putInt(Object obj, long offset, int newValue);

    /**
     * Lazy set an int field.
     *
     * @param obj      non-{@code null}; object containing the field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putOrderedInt(Object obj, long offset, int newValue);

    /**
     * Gets a {@code long} field from the given object.
     *
     * @param obj    non-{@code null}; object containing the field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native long getLong(Object obj, long offset);

    /**
     * Stores a {@code long} field into the given object.
     *
     * @param obj      non-{@code null}; object containing the field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putLong(Object obj, long offset, long newValue);

    /**
     * Lazy set a long field.
     *
     * @param obj      non-{@code null}; object containing the field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putOrderedLong(Object obj, long offset, long newValue);

    /**
     * Gets an {@code obj} field from the given object.
     *
     * @param obj    non-{@code null}; object containing the field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native Object getObject(Object obj, long offset);

    /**
     * Stores an {@code obj} field into the given object.
     *
     * @param obj      non-{@code null}; object containing the field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putObject(Object obj, long offset, Object newValue);

    /**
     * Lazy set an object field.
     *
     * @param obj      non-{@code null}; object containing the field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putOrderedObject(Object obj, long offset,
                                        Object newValue);

    /**
     * Gets a {@code boolean} field from the given object.
     *
     * @param obj    non-{@code null}; object containing boolean field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native boolean getBoolean(Object obj, long offset);

    /**
     * Stores a {@code boolean} field into the given object.
     *
     * @param obj      non-{@code null}; object containing boolean field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putBoolean(Object obj, long offset, boolean newValue);

    /**
     * Gets a {@code byte} field from the given object.
     *
     * @param obj    non-{@code null}; object containing byte field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native byte getByte(Object obj, long offset);

    /**
     * Stores a {@code byte} field into the given object.
     *
     * @param obj      non-{@code null}; object containing byte field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putByte(Object obj, long offset, byte newValue);

    /**
     * Gets a {@code char} field from the given object.
     *
     * @param obj    non-{@code null}; object containing char field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native char getChar(Object obj, long offset);

    /**
     * Stores a {@code char} field into the given object.
     *
     * @param obj      non-{@code null}; object containing char field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putChar(Object obj, long offset, char newValue);

    /**
     * Gets a {@code short} field from the given object.
     *
     * @param obj    non-{@code null}; object containing short field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native short getShort(Object obj, long offset);

    /**
     * Stores a {@code short} field into the given object.
     *
     * @param obj      non-{@code null}; object containing short field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putShort(Object obj, long offset, short newValue);

    /**
     * Gets a {@code float} field from the given object.
     *
     * @param obj    non-{@code null}; object containing float field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native float getFloat(Object obj, long offset);

    /**
     * Stores a {@code float} field into the given object.
     *
     * @param obj      non-{@code null}; object containing float field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putFloat(Object obj, long offset, float newValue);

    /**
     * Gets a {@code double} field from the given object.
     *
     * @param obj    non-{@code null}; object containing double field
     * @param offset offset to the field within {@code obj}
     * @return the retrieved value
     */
    public native double getDouble(Object obj, long offset);

    /**
     * Stores a {@code double} field into the given object.
     *
     * @param obj      non-{@code null}; object containing double field
     * @param offset   offset to the field within {@code obj}
     * @param newValue the value to store
     */
    public native void putDouble(Object obj, long offset, double newValue);

    /**
     * Parks the calling thread for the specified amount of time,
     * unless the "permit" for the thread is already available (due to
     * a previous call to {@link #unpark}. This method may also return
     * spuriously (that is, without the thread being told to unpark
     * and without the indicated amount of time elapsing).
     *
     * <p>See {@link java.util.concurrent.locks.LockSupport} for more
     * in-depth information of the behavior of this method.</p>
     *
     * @param absolute whether the given time value is absolute
     *                 milliseconds-since-the-epoch ({@code true}) or relative
     *                 nanoseconds-from-now ({@code false})
     * @param time     the (absolute millis or relative nanos) time value
     */
    public native void park(boolean absolute, long time);

    /**
     * Unparks the given object, which must be a {@link Thread}.
     *
     * <p>See {@link java.util.concurrent.locks.LockSupport} for more
     * in-depth information of the behavior of this method.</p>
     *
     * @param obj non-{@code null}; the object to unpark
     */
    public native void unpark(Object obj);

    /**
     * Allocates an instance of the given class without running the constructor.
     * The class' <clinit> will be run, if necessary.
     */
    public native Object allocateInstance(Class<?> c);

    /**
     * Gets the size of the address value, in bytes.
     *
     * @return the size of the address, in bytes
     */
    public native int addressSize();

    /**
     * Gets the size of the memory page, in bytes.
     *
     * @return the size of the page
     */
    public native int pageSize();

    /**
     * Allocates a memory block of size {@code bytes}.
     *
     * @param bytes size of the memory block
     * @return address of the allocated memory
     */
    public native long allocateMemory(long bytes);

    /**
     * Frees previously allocated memory at given address.
     *
     * @param address address of the freed memory
     */
    public native void freeMemory(long address);

    /**
     * Fills given memory block with a given value.
     *
     * @param address address of the memoory block
     * @param bytes   length of the memory block, in bytes
     * @param value   fills memory with this value
     */
    public native void setMemory(long address, long bytes, byte value);

    /**
     * Gets {@code byte} from given address in memory.
     *
     * @param address address in memory
     * @return {@code byte} value
     */
    public native byte getByte(long address);

    /**
     * Stores a {@code byte} into the given memory address.
     *
     * @param address  address in memory where to store the value
     * @param newValue the value to store
     */
    public native void putByte(long address, byte x);

    /**
     * Gets {@code short} from given address in memory.
     *
     * @param address address in memory
     * @return {@code short} value
     */
    public native short getShort(long address);

    /**
     * Stores a {@code short} into the given memory address.
     *
     * @param address  address in memory where to store the value
     * @param newValue the value to store
     */
    public native void putShort(long address, short x);

    /**
     * Gets {@code char} from given address in memory.
     *
     * @param address address in memory
     * @return {@code char} value
     */
    public native char getChar(long address);

    /**
     * Stores a {@code char} into the given memory address.
     *
     * @param address  address in memory where to store the value
     * @param newValue the value to store
     */
    public native void putChar(long address, char x);

    /**
     * Gets {@code int} from given address in memory.
     *
     * @param address address in memory
     * @return {@code int} value
     */
    public native int getInt(long address);

    /**
     * Stores a {@code int} into the given memory address.
     *
     * @param address  address in memory where to store the value
     * @param newValue the value to store
     */
    public native void putInt(long address, int x);

    /**
     * Gets {@code long} from given address in memory.
     *
     * @param address address in memory
     * @return {@code long} value
     */
    public native long getLong(long address);

    /**
     * Stores a {@code long} into the given memory address.
     *
     * @param address  address in memory where to store the value
     * @param newValue the value to store
     */
    public native void putLong(long address, long x);

    /**
     * Gets {@code long} from given address in memory.
     *
     * @param address address in memory
     * @return {@code long} value
     */
    public native float getFloat(long address);

    /**
     * Stores a {@code float} into the given memory address.
     *
     * @param address  address in memory where to store the value
     * @param newValue the value to store
     */
    public native void putFloat(long address, float x);

    /**
     * Gets {@code double} from given address in memory.
     *
     * @param address address in memory
     * @return {@code double} value
     */
    public native double getDouble(long address);

    /**
     * Stores a {@code double} into the given memory address.
     *
     * @param address  address in memory where to store the value
     * @param newValue the value to store
     */
    public native void putDouble(long address, double x);

    /**
     * Copies given memory block to a primitive array.
     *
     * @param srcAddr   address to copy memory from
     * @param dst       address to copy memory to
     * @param dstOffset offset in {@code dst}
     * @param bytes     number of bytes to copy
     */
    public native void copyMemoryToPrimitiveArray(long srcAddr,
                                                  Object dst, long dstOffset, long bytes);

    /**
     * Treat given primitive array as a continuous memory block and
     * copy it to given memory address.
     *
     * @param src       primitive array to copy data from
     * @param srcOffset offset in {@code src} to copy from
     * @param dstAddr   memory address to copy data to
     * @param bytes     number of bytes to copy
     */
    public native void copyMemoryFromPrimitiveArray(Object src, long srcOffset,
                                                    long dstAddr, long bytes);

    /**
     * Sets all bytes in a given block of memory to a copy of another block.
     *
     * @param srcAddr address of the source memory to be copied from
     * @param dstAddr address of the destination memory to copy to
     * @param bytes   number of bytes to copy
     */
    public native void copyMemory(long srcAddr, long dstAddr, long bytes);
    // The following contain CAS-based Java implementations used on
    // platforms not supporting native instructions

    /**
     * Atomically adds the given value to the current value of a field
     * or array element within the given object {@code o}
     * at the given {@code offset}.
     *
     * @param o      object/array to update the field/element in
     * @param offset field/element offset
     * @param delta  the value to add
     * @return the previous value
     * @since 1.8
     */
    // @HotSpotIntrinsicCandidate
    public final int getAndAddInt(Object o, long offset, int delta) {
        int v;
        do {
            v = getIntVolatile(o, offset);
        } while (!compareAndSwapInt(o, offset, v, v + delta));
        return v;
    }

    /**
     * Atomically adds the given value to the current value of a field
     * or array element within the given object {@code o}
     * at the given {@code offset}.
     *
     * @param o      object/array to update the field/element in
     * @param offset field/element offset
     * @param delta  the value to add
     * @return the previous value
     * @since 1.8
     */
    // @HotSpotIntrinsicCandidate
    public final long getAndAddLong(Object o, long offset, long delta) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        } while (!compareAndSwapLong(o, offset, v, v + delta));
        return v;
    }

    /**
     * Atomically exchanges the given value with the current value of
     * a field or array element within the given object {@code o}
     * at the given {@code offset}.
     *
     * @param o        object/array to update the field/element in
     * @param offset   field/element offset
     * @param newValue new value
     * @return the previous value
     * @since 1.8
     */
    // @HotSpotIntrinsicCandidate
    public final int getAndSetInt(Object o, long offset, int newValue) {
        int v;
        do {
            v = getIntVolatile(o, offset);
        } while (!compareAndSwapInt(o, offset, v, newValue));
        return v;
    }

    /**
     * Atomically exchanges the given value with the current value of
     * a field or array element within the given object {@code o}
     * at the given {@code offset}.
     *
     * @param o        object/array to update the field/element in
     * @param offset   field/element offset
     * @param newValue new value
     * @return the previous value
     * @since 1.8
     */
    // @HotSpotIntrinsicCandidate
    public final long getAndSetLong(Object o, long offset, long newValue) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        } while (!compareAndSwapLong(o, offset, v, newValue));
        return v;
    }

    /**
     * Atomically exchanges the given reference value with the current
     * reference value of a field or array element within the given
     * object {@code o} at the given {@code offset}.
     *
     * @param o        object/array to update the field/element in
     * @param offset   field/element offset
     * @param newValue new value
     * @return the previous value
     * @since 1.8
     */
    // @HotSpotIntrinsicCandidate
    public final Object getAndSetObject(Object o, long offset, Object newValue) {
        Object v;
        do {
            v = getObjectVolatile(o, offset);
        } while (!compareAndSwapObject(o, offset, v, newValue));
        return v;
    }

    /**
     * Ensures that loads before the fence will not be reordered with loads and
     * stores after the fence; a "LoadLoad plus LoadStore barrier".
     * <p>
     * Corresponds to C11 atomic_thread_fence(memory_order_acquire)
     * (an "acquire fence").
     * <p>
     * A pure LoadLoad fence is not provided, since the addition of LoadStore
     * is almost always desired, and most current hardware instructions that
     * provide a LoadLoad barrier also provide a LoadStore barrier for free.
     *
     * @since 1.8
     */
    // @HotSpotIntrinsicCandidate
    public native void loadFence();

    /**
     * Ensures that loads and stores before the fence will not be reordered with
     * stores after the fence; a "StoreStore plus LoadStore barrier".
     * <p>
     * Corresponds to C11 atomic_thread_fence(memory_order_release)
     * (a "release fence").
     * <p>
     * A pure StoreStore fence is not provided, since the addition of LoadStore
     * is almost always desired, and most current hardware instructions that
     * provide a StoreStore barrier also provide a LoadStore barrier for free.
     *
     * @since 1.8
     */
    // @HotSpotIntrinsicCandidate
    public native void storeFence();

    /**
     * Ensures that loads and stores before the fence will not be reordered
     * with loads and stores after the fence.  Implies the effects of both
     * loadFence() and storeFence(), and in addition, the effect of a StoreLoad
     * barrier.
     * <p>
     * Corresponds to C11 atomic_thread_fence(memory_order_seq_cst).
     *
     * @since 1.8
     */
    // @HotSpotIntrinsicCandidate
    public native void fullFence();
}