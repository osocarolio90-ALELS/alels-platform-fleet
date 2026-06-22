package com.alels.gateway.util;

public class ByteUtil {

    private final byte[] data;
    private int index;

    public ByteUtil(byte[] data, int startIndex) {
        this.data = data;
        this.index = startIndex;
    }

    public int position() {
        return index;
    }

    public int readUInt8() {
        return data[index++] & 0xFF;
    }

    public int readUInt16() {
        int value =
                ((data[index] & 0xFF) << 8)
                        | (data[index + 1] & 0xFF);

        index += 2;

        return value;
    }

    public int readInt32() {
        int value =
                ((data[index] & 0xFF) << 24)
                        | ((data[index + 1] & 0xFF) << 16)
                        | ((data[index + 2] & 0xFF) << 8)
                        | (data[index + 3] & 0xFF);

        index += 4;

        return value;
    }

    public long readUInt32() {
        long value =
                ((long) (data[index] & 0xFF) << 24)
                        | ((long) (data[index + 1] & 0xFF) << 16)
                        | ((long) (data[index + 2] & 0xFF) << 8)
                        | ((long) data[index + 3] & 0xFF);

        index += 4;

        return value;
    }

    public long readInt64() {
        long value = 0;

        for (int i = 0; i < 8; i++) {
            value = (value << 8)
                    | (data[index++] & 0xFF);
        }

        return value;
    }

    public byte[] readBytes(int length) {
        byte[] result = new byte[length];

        System.arraycopy(
                data,
                index,
                result,
                0,
                length
        );

        index += length;

        return result;
    }
}