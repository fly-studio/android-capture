package org.fly.android.localvpn.structs;

import java.nio.ByteBuffer;

public class BufferUtils {
    // --------------------------ByteBuffer Byte---------------------------------

    public static short getUnsignedByte(ByteBuffer bb) {
        return ((short) (bb.get() & 0xff));
    }

    public static void putUnsignedByte(ByteBuffer bb, int value) {
        bb.put((byte) (value & 0xff));
    }

    public static short getUnsignedByte(ByteBuffer bb, int position) {
        return ((short) (bb.get(position) & (short) 0xff));
    }

    public static void putUnsignedByte(ByteBuffer bb, int position, int value) {
        bb.put(position, (byte) (value & 0xff));
    }

    // --------------------------ByteBuffer Short---------------------------------

    public static int getUnsignedShort(ByteBuffer bb) {
        return (bb.getShort() & 0xffff);
    }

    public static void putUnsignedShort(ByteBuffer bb, int value) {
        bb.putShort((short) (value & 0xffff));
    }

    public static int getUnsignedShort(ByteBuffer bb, int position) {
        return (bb.getShort(position) & 0xffff);
    }

    public static void putUnsignedShort(ByteBuffer bb, int position, int value) {
        bb.putShort(position, (short) (value & 0xffff));
    }

    // --------------------------ByteBuffer Int---------------------------------


    public static long getUnsignedInt(ByteBuffer bb) {
        return ((long) bb.getInt() & 0xffffffffL);
    }

    public static void putUnsignedInt(ByteBuffer bb, long value) {
        bb.putInt((int) (value & 0xffffffffL));
    }

    public static long getUnsignedInt(ByteBuffer bb, int position) {
        return ((long) bb.getInt(position) & 0xffffffffL);
    }

    public static void putUnsignedInt(ByteBuffer bb, int position, long value) {
        bb.putInt(position, (int) (value & 0xffffffffL));
    }

    // --------------------------IoBuffer Byte---------------------------------

    public static short getUnsignedByte(IoBuffer bb) {
        return ((short) (bb.get() & 0xff));
    }

    public static void putUnsignedByte(IoBuffer bb, int value) {
        bb.put((byte) (value & 0xff));
    }

    public static short getUnsignedByte(IoBuffer bb, int position) {
        return ((short) (bb.get(position) & (short) 0xff));
    }

    public static void putUnsignedByte(IoBuffer bb, int position, int value) {
        bb.put(position, (byte) (value & 0xff));
    }

    // --------------------------IoBuffer Short---------------------------------

    public static int getUnsignedShort(IoBuffer bb) {
        return (bb.getShort() & 0xffff);
    }

    public static void putUnsignedShort(IoBuffer bb, int value) {
        bb.putShort((short) (value & 0xffff));
    }

    public static int getUnsignedShort(IoBuffer bb, int position) {
        return (int)(bb.getShort(position) & 0xffff);
    }

    public static void putUnsignedShort(IoBuffer bb, int position, int value) {
        bb.putShort(position, (short) (value & 0xffff));
    }

    // --------------------------IoBuffer Int---------------------------------

    public static long getUnsignedInt(IoBuffer bb) {
        return ((long) bb.getInt() & 0xffffffffL);
    }

    public static void putUnsignedInt(IoBuffer bb, long value) {
        bb.putInt((int) (value & 0xffffffffL));
    }

    public static long getUnsignedInt(IoBuffer bb, int position) {
        return ((long) bb.getInt(position) & 0xffffffffL);
    }

    public static void putUnsignedInt(IoBuffer bb, int position, long value) {
        bb.putInt(position, (int) (value & 0xffffffffL));
    }
}
