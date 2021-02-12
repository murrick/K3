package org.kanger.storage;

import org.kanger.exception.OutOfBufferException;

import java.io.ObjectInput;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Stack;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class ByteBuffer {

    private final Stack<Integer> mark;
    private boolean reverse = false;
    private byte[] buffer;
    private int length;
    private int i;

    public ByteBuffer() {
        buffer = new byte[]{};
        i = 0;
        length = 0;
        mark = new Stack<>();
    }

    public ByteBuffer(byte[] buffer) {
        this.buffer = buffer;
        i = 0;
        length = buffer.length;
        mark = new Stack<>();
    }

    public ByteBuffer(byte[] buffer, int offset) {
        this.buffer = buffer;
        i = offset;
        length = buffer.length;
        mark = new Stack<>();
    }

    public ByteBuffer(ObjectInput dis) throws Exception {
        i = 0;
        buffer = new byte[2];
        mark = new Stack<>();
        dis.read(buffer);
        int len = getWord();
        if (len == -1) {
            buffer = new byte[4];
            dis.read(buffer);
            len = (int) getDWord();
        }
        if (len > 0) {
            buffer = new byte[len];
            dis.read(buffer);
        } else {
            buffer = new byte[]{};
        }

        i = 0;
        length = buffer.length;
    }

    public ByteBuffer(String str) {
        str = str.replace("-", "");
        str = str.replace("\n", "");
        str = str.replace("\\n", "");
        str = str.replace("\r", "");
        str = str.replace(" ", "");
        buffer = new byte[str.length() / 2 + (str.length() % 2 == 0 ? 0 : 1)];
        for (int i = 0; i < buffer.length; ++i) {
            String sub = str.substring(i * 2);
            if (sub.length() == 1) {
                sub = "0" + sub;
            } else if (sub.length() > 2) {
                sub = sub.substring(0, 2);
            }
            buffer[i] = (byte) (Integer.parseInt(sub, 16) & 0xFF);
        }
        i = 0;
        length = buffer.length;
        mark = new Stack<>();
    }

    public static byte[] reverse(byte[] buffer, int len) {
        if (len > 0) {
            byte[] tmp = new byte[len];
            for (int i = 0; i < len; ++i) {
                tmp[len - i - 1] = buffer[i];
            }
            return tmp;
        } else {
            return buffer;
        }
    }

//    public static byte[] copy(byte[] str, int offset, int len) {
//        byte[] dst = new byte[len];
//        System.arraycopy(str, offset, dst, 0, len);
//        return dst;
//    }
//
//    public static byte[] copy(byte[] str, int offset) {
//        int len = str.length - offset;
//        byte[] dst = new byte[len];
//        System.arraycopy(str, offset, dst, 0, len);
//        return dst;
//    }

    public static ByteBuffer fromHex(String hex) {
        return new ByteBuffer(hex);
    }

    public static ByteBuffer fromBase64(String base) {
        byte[] data = Base64.getDecoder().decode(base);
        return new ByteBuffer(data);
    }

    public int length() {
        return !mark.isEmpty() ? mark.peek() : length;
    }

    public int rest() {
        return length() - i;
    }

    public ByteBuffer mark() throws OutOfBufferException {
        int len = getWord();
        if (len == -1) {
            len = (int) getDWord();
        }
        mark.push(i + len);
        return this;
    }

    public ByteBuffer release() {
        if (!mark.isEmpty()) {
            i = mark.pop();
        }
        return this;
    }

    public ByteBuffer drop() {
        if (!mark.isEmpty()) {
            mark.clear();
        }
        return this;
    }

    public ByteBuffer cut() {
        length = i;
        return this;
    }

//    public ByteBuffer cut(int pos) {
//        length = pos;
//        return this;
//    }

    public byte[] getBytes() throws OutOfBufferException {
        if (rest() < 2) {
            throw new OutOfBufferException("need 2, rest " + rest() + ", length " + length() + ", buffer " + length);
        }
        int len = getWord();
        if (len == -1) {
            if (rest() < 4) {
                throw new OutOfBufferException("need 4, rest " + rest() + ", length " + length() + ", buffer " + length);
            }
            len = (int) getDWord();
        }
        return getBytes(len);
    }

    public byte[] getBytes(int len) throws OutOfBufferException {
        if (len > 0) {
            if (len > rest()) {
                throw new OutOfBufferException("need " + len + ", rest " + rest() + ", length " + length() + ", buffer " + length);
            }
            byte[] packet = new byte[len];
            System.arraycopy(buffer, i, packet, 0, len);
            i += len;
            return packet;

        } else {
            return new byte[]{};
        }
    }

    public int getByte() throws OutOfBufferException {
        if (rest() < 1) {
            throw new OutOfBufferException("need 1, rest " + rest() + ", length " + length() + ", buffer " + length);
        }
        int r = (buffer[i] & 0xFF);
        i += 1;
        if (r == 0xFF) {
            r = -1;
        }
        return r;
    }

    public int getWord() throws OutOfBufferException {
        if (rest() < 2) {
            throw new OutOfBufferException("need 2, rest " + rest() + ", length " + length() + ", buffer " + length);
        }
        int r = reverse
                ? ((buffer[i] & 0xFF) << 8) | (buffer[i + 1] & 0x00FF) & 0xFFFF
                : (buffer[i] & 0xFF) | ((buffer[i + 1] << 8) & 0xFF00) & 0xFFFF;
        i += 2;
        if (r == 0xFFFF) {
            r = -1;
        }
        return r;
    }

    public long getDWord() throws OutOfBufferException {
        if (rest() < 4) {
            throw new OutOfBufferException("need 4, rest " + rest() + ", length " + length() + ", buffer " + length);
        }
        long r = reverse
                ? (buffer[i + 3] & 0xFF) | ((buffer[i + 2] << 8) & 0xFF00) | ((buffer[i + 1] << 16) & 0xFF0000) | ((buffer[i] << 24) & 0xFF000000) & 0xFFFFFFFFL
                : (buffer[i] & 0xFF) | ((buffer[i + 1] << 8) & 0xFF00) | ((buffer[i + 2] << 16) & 0xFF0000) | ((buffer[i + 3] << 24) & 0xFF000000) & 0xFFFFFFFFL;
        i += 4;
        if (r == 0xFFFFFFFFL) {
            r = -1;
        }
        return r;
    }

    public long get3Word() throws OutOfBufferException {
        if (rest() < 3) {
            throw new OutOfBufferException("need 3, rest " + rest() + ", length " + length() + ", buffer " + length);
        }
        long r = reverse
                ? (buffer[i + 2] & 0xFF) | ((buffer[i + 1] << 8) & 0xFF00) | ((buffer[i] << 16) & 0xFF0000) & 0xFFFFFFL
                : (buffer[i] & 0xFF) | ((buffer[i + 1] << 8) & 0xFF00) | ((buffer[i + 2] << 16) & 0xFF0000) & 0xFFFFFFL;
        i += 3;
        if (r == 0xFFFFFFFFL) {
            r = -1;
        }
        return r;
    }

    public long get6Word() throws OutOfBufferException {
        if (rest() < 6) {
            throw new OutOfBufferException("need 6, rest " + rest() + ", length " + length() + ", buffer " + length);
        }
        long r = reverse
                ? (long) (buffer[i + 5] & 0xFF)
                | (long) (buffer[i + 4] & 0xFF) << 8
                | (long) (buffer[i + 3] & 0xFF) << 16
                | (long) (buffer[i + 2] & 0xFF) << 24
                | (long) (buffer[i + 1] & 0xFF) << 32
                | (long) (buffer[i + 0] & 0xFF) << 40
                : (long) (buffer[i + 0] & 0xFF)
                | (long) (buffer[i + 1] & 0xFF) << 8
                | (long) (buffer[i + 2] & 0xFF) << 16
                | (long) (buffer[i + 3] & 0xFF) << 24
                | (long) (buffer[i + 4] & 0xFF) << 32
                | (long) (buffer[i + 5] & 0xFF) << 40;
        i += 6;
        if (r == 0xFFFFFFFFL) {
            r = -1;
        }
        return r;
    }

    public double getDouble() throws OutOfBufferException {
        if (rest() < 8) {
            throw new OutOfBufferException("need 8, rest " + rest() + ", length " + length() + ", buffer " + length);
        }
        long z = reverse
                ? buffer[i + 7] & 0xFF
                | (long) (buffer[i + 6] & 0xFF) << 8
                | (long) (buffer[i + 5] & 0xFF) << 16
                | (long) (buffer[i + 4] & 0xFF) << 24
                | (long) (buffer[i + 3] & 0xFF) << 32
                | (long) (buffer[i + 2] & 0xFF) << 40
                | (long) (buffer[i + 1] & 0xFF) << 48
                | (long) (buffer[i + 0] & 0xFF) << 56
                : buffer[i + 0] & 0xFF
                | (long) (buffer[i + 1] & 0xFF) << 8
                | (long) (buffer[i + 2] & 0xFF) << 16
                | (long) (buffer[i + 3] & 0xFF) << 24
                | (long) (buffer[i + 4] & 0xFF) << 32
                | (long) (buffer[i + 5] & 0xFF) << 40
                | (long) (buffer[i + 6] & 0xFF) << 48
                | (long) (buffer[i + 7] & 0xFF) << 56;
        i += 8;
        return Double.longBitsToDouble(z);
    }

    public float getFloat() throws OutOfBufferException {
        if (rest() < 4) {
            throw new OutOfBufferException("need 4, rest " + rest() + ", length " + length() + ", buffer " + length);
        }
        int z = reverse
                ? buffer[i + 3] & 0xFF
                | (int) (buffer[i + 2] & 0xFF) << 8
                | (int) (buffer[i + 1] & 0xFF) << 16
                | (int) (buffer[i + 0] & 0xFF) << 24
                : buffer[i + 0] & 0xFF
                | (int) (buffer[i + 1] & 0xFF) << 8
                | (int) (buffer[i + 2] & 0xFF) << 16
                | (int) (buffer[i + 3] & 0xFF) << 24;
        i += 4;
        return Float.intBitsToFloat(z);
    }

    public long getLong() throws OutOfBufferException {
        if (rest() < 8) {
            throw new OutOfBufferException("need 8, rest " + rest() + ", length " + length() + ", buffer " + length);
        }
        long z = reverse
                ? buffer[i + 7] & 0xFF
                | (long) (buffer[i + 6] & 0xFF) << 8
                | (long) (buffer[i + 5] & 0xFF) << 16
                | (long) (buffer[i + 4] & 0xFF) << 24
                | (long) (buffer[i + 3] & 0xFF) << 32
                | (long) (buffer[i + 2] & 0xFF) << 40
                | (long) (buffer[i + 1] & 0xFF) << 48
                | (long) (buffer[i + 0] & 0xFF) << 56
                : buffer[i + 0] & 0xFF
                | (long) (buffer[i + 1] & 0xFF) << 8
                | (long) (buffer[i + 2] & 0xFF) << 16
                | (long) (buffer[i + 3] & 0xFF) << 24
                | (long) (buffer[i + 4] & 0xFF) << 32
                | (long) (buffer[i + 5] & 0xFF) << 40
                | (long) (buffer[i + 6] & 0xFF) << 48
                | (long) (buffer[i + 7] & 0xFF) << 56;
        i += 8;
        return z;
    }

    public int getInt() throws OutOfBufferException {
        return (int) getDWord();
    }

    public ByteBuffer putInt(int val) {
        return putDWord(val);
    }

    public String getString() throws OutOfBufferException {
        return new String(getBytes());
    }

    public String getString(String codepage) throws OutOfBufferException, UnsupportedEncodingException {
        return new String(getBytes(), codepage);
    }

    public ByteBuffer putBytes(byte[] packet) {
        if (packet.length > 0xFFFF) {
            putWord(-1);
            putDWord(packet.length);
        } else {
            putWord(packet.length);
        }
        _append(packet);
        return this;
    }

    public ByteBuffer putByte(int x) {
        byte[] tmp = new byte[1];
        tmp[0] = (byte) (x & 0xFF);
        _append(tmp);
        return this;
    }

    public ByteBuffer putDWord(long b) {
        byte[] tmp = new byte[4];
        if (!reverse) {
            tmp[0] = (byte) (b & 0xFF);
            tmp[1] = (byte) ((b >> 8) & 0xFF);
            tmp[2] = (byte) ((b >> 16) & 0xFF);
            tmp[3] = (byte) ((b >> 24) & 0xFF);
        } else {
            tmp[3] = (byte) (b & 0xFF);
            tmp[2] = (byte) ((b >> 8) & 0xFF);
            tmp[1] = (byte) ((b >> 16) & 0xFF);
            tmp[0] = (byte) ((b >> 24) & 0xFF);
        }
        _append(tmp);
        return this;
    }

    public ByteBuffer put3Word(long b) {
        byte[] tmp = new byte[3];
        if (!reverse) {
            tmp[0] = (byte) (b & 0xFF);
            tmp[1] = (byte) ((b >> 8) & 0xFF);
            tmp[2] = (byte) ((b >> 16) & 0xFF);
        } else {
            tmp[2] = (byte) (b & 0xFF);
            tmp[1] = (byte) ((b >> 8) & 0xFF);
            tmp[0] = (byte) ((b >> 16) & 0xFF);
        }
        _append(tmp);
        return this;
    }

    public ByteBuffer put6Word(long b) {
        byte[] tmp = new byte[6];
        if (!reverse) {
            tmp[0] = (byte) (b & 0xFF);
            tmp[1] = (byte) ((b >> 8) & 0xFF);
            tmp[2] = (byte) ((b >> 16) & 0xFF);
            tmp[3] = (byte) ((b >> 24) & 0xFF);
            tmp[4] = (byte) ((b >> 32) & 0xFF);
            tmp[5] = (byte) ((b >> 40) & 0xFF);
        } else {
            tmp[5] = (byte) (b & 0xFF);
            tmp[4] = (byte) ((b >> 8) & 0xFF);
            tmp[3] = (byte) ((b >> 16) & 0xFF);
            tmp[2] = (byte) ((b >> 24) & 0xFF);
            tmp[1] = (byte) ((b >> 32) & 0xFF);
            tmp[0] = (byte) ((b >> 40) & 0xFF);
        }
        _append(tmp);
        return this;
    }

    public ByteBuffer putWord(int b) {
        byte[] tmp = new byte[2];
        if (!reverse) {
            tmp[0] = (byte) (b & 0xFF);
            tmp[1] = (byte) ((b >> 8) & 0xFF);
        } else {
            tmp[1] = (byte) (b & 0xFF);
            tmp[0] = (byte) ((b >> 8) & 0xFF);
        }
        _append(tmp);
        return this;
    }

    public ByteBuffer putDouble(Double d) {
        long b = Double.doubleToLongBits(d);
        byte[] tmp = new byte[8];
        if (!reverse) {
            tmp[0] = (byte) (b & 0xFF);
            tmp[1] = (byte) ((b >> 8) & 0xFF);
            tmp[2] = (byte) ((b >> 16) & 0xFF);
            tmp[3] = (byte) ((b >> 24) & 0xFF);
            tmp[4] = (byte) ((b >> 32) & 0xFF);
            tmp[5] = (byte) ((b >> 40) & 0xFF);
            tmp[6] = (byte) ((b >> 48) & 0xFF);
            tmp[7] = (byte) ((b >> 56) & 0xFF);
        } else {
            tmp[7] = (byte) (b & 0xFF);
            tmp[6] = (byte) ((b >> 8) & 0xFF);
            tmp[5] = (byte) ((b >> 16) & 0xFF);
            tmp[4] = (byte) ((b >> 24) & 0xFF);
            tmp[3] = (byte) ((b >> 32) & 0xFF);
            tmp[2] = (byte) ((b >> 40) & 0xFF);
            tmp[1] = (byte) ((b >> 48) & 0xFF);
            tmp[0] = (byte) ((b >> 56) & 0xFF);
        }
        _append(tmp);
        return this;
    }

    public ByteBuffer putFloat(Float d) {
        long b = Float.floatToIntBits(d);
        byte[] tmp = new byte[4];
        if (!reverse) {
            tmp[0] = (byte) (b & 0xFF);
            tmp[1] = (byte) ((b >> 8) & 0xFF);
            tmp[2] = (byte) ((b >> 16) & 0xFF);
            tmp[3] = (byte) ((b >> 24) & 0xFF);
        } else {
            tmp[3] = (byte) (b & 0xFF);
            tmp[2] = (byte) ((b >> 8) & 0xFF);
            tmp[1] = (byte) ((b >> 16) & 0xFF);
            tmp[0] = (byte) ((b >> 24) & 0xFF);
        }
        _append(tmp);
        return this;
    }

    public ByteBuffer putLong(long b) {
        byte[] tmp = new byte[8];
        if (!reverse) {
            tmp[0] = (byte) (b & 0xFF);
            tmp[1] = (byte) ((b >> 8) & 0xFF);
            tmp[2] = (byte) ((b >> 16) & 0xFF);
            tmp[3] = (byte) ((b >> 24) & 0xFF);
            tmp[4] = (byte) ((b >> 32) & 0xFF);
            tmp[5] = (byte) ((b >> 40) & 0xFF);
            tmp[6] = (byte) ((b >> 48) & 0xFF);
            tmp[7] = (byte) ((b >> 56) & 0xFF);
        } else {
            tmp[7] = (byte) (b & 0xFF);
            tmp[6] = (byte) ((b >> 8) & 0xFF);
            tmp[5] = (byte) ((b >> 16) & 0xFF);
            tmp[4] = (byte) ((b >> 24) & 0xFF);
            tmp[3] = (byte) ((b >> 32) & 0xFF);
            tmp[2] = (byte) ((b >> 40) & 0xFF);
            tmp[1] = (byte) ((b >> 48) & 0xFF);
            tmp[0] = (byte) ((b >> 56) & 0xFF);
        }
        _append(tmp);
        return this;
    }

    public ByteBuffer putString(String str) {
        putBytes(str.getBytes());
        return this;
    }

    public ByteBuffer putZString(String str) {
        ByteBuffer tmp = new ByteBuffer();
        try {
            tmp.append(str.getBytes("windows-1251"));
            tmp.putByte(0);
        } catch (UnsupportedEncodingException ex) {
            //
        }
        _append(tmp.getBuffer());
        return this;
    }

    public ByteBuffer putShortString(String str) {
        ByteBuffer tmp = new ByteBuffer();
        if (str.length() > 0x7F) {
            tmp.putByte(0xFF);
            tmp.putWord(str.length());
        } else {
            tmp.putByte(str.length());
        }
        try {
            tmp.append(str.getBytes("windows-1251"));
        } catch (UnsupportedEncodingException ex) {
            //
        }
        _append(tmp.getBuffer());
        return this;
    }

    public String getZString() throws OutOfBufferException {
        ByteBuffer tmp = new ByteBuffer();
        while (!isEod()) {
            int c = getByte();
            if (c != 0) {
                tmp.putByte(c);
            } else {
                break;
            }
        }
        try {
            return new String(tmp.getBuffer(), "windows-1251");
        } catch (UnsupportedEncodingException ex) {
            return "";
        }
    }

    public String getZString(int max) throws OutOfBufferException {
        ByteBuffer tmp = new ByteBuffer();
        for (int i = 0; i < max && !isEod(); ++i) {
            int c = getByte();
            if (c != 0) {
                tmp.putByte(c);
            } else {
                break;
            }
        }
        try {
            return new String(tmp.getBuffer(), "windows-1251");
        } catch (UnsupportedEncodingException ex) {
            return "";
        }
    }

    public String getShortString() throws OutOfBufferException {
        int len = getByte() & 0xFF;
        i += 1;
        if (len == 0xFF) {
            len = (int) getWord();
            i += 2;
        }
        String str = "";
        if (len > 0) {
            try {
                byte[] dst = new byte[len];
                System.arraycopy(buffer, i, dst, 0, len);
                str = len > 0 ? new String(dst, "windows-1251") : "";
                i += len;
            } catch (UnsupportedEncodingException ex) {
                ex.printStackTrace(System.err);
            }
        }
        return str;
    }

    public ByteBuffer append(ByteBuffer tail) {
        _append(tail.buffer);
        return this;
    }

    public ByteBuffer append(byte[] packet) {
        _append(packet);
        return this;
    }

//    public ByteBuffer append(byte[] packet, int start, int length) {
//        buffer = _append(buffer, packet, start, length);
//        return this;
//    }
//
//    public ByteBuffer append(ByteBuffer tail, int start, int length) {
//        buffer = _append(buffer, tail.buffer, start, length);
//        return this;
//    }

    public byte[] getBuffer() {
        if (!reverse) {
            return buffer;
        } else {
            return reverse(buffer, buffer.length);
        }
    }

    public ByteBuffer reverse(boolean on) {
        reverse = on;
        return this;
    }

    public ByteBuffer createMarked() {
        return new ByteBuffer()
                .putBytes(buffer);
    }

    public ByteBuffer incPos(int delta) {
        this.i += delta;
        return this;
    }

    public int getPos() {
        return i;
    }

    public ByteBuffer setPos(int pos) {
        this.i = pos;
        return this;
    }

    public int getPartsCount(int partSize) {
        return (length / partSize) + (length % partSize > 0 ? 1 : 0);
    }

    public byte[] getPart(int index, int partSize) {
        int len = length - partSize * index;
        if (len > 0) {
            len = len > partSize ? partSize : len;
            byte[] dst = new byte[len];
            System.arraycopy(buffer, partSize * index, dst, 0, len);
            return dst;
        } else {
            return new byte[]{};
        }
    }

    public String toHexFormated() {
        String s = "";
        int pos = 0;
        for (byte x : buffer) {
            if (pos != 0 && pos % 16 == 0) {
                s += "\n";
            } else if (pos != 0 && pos % 2 == 0) {
                s += '-';
            }
            s += String.format("%02X", x & 0xFF);
            pos++;
        }
        return s;
    }

    public String toHex() {
        String s = "";
        for (byte x : buffer) {
            s += String.format("%02X", x & 0xFF);
        }
        return s;
    }

    public String toBase64() throws UnsupportedEncodingException {
        return Base64.getEncoder().encodeToString(buffer);
    }

    public ByteBuffer clear() {
        buffer = new byte[]{};
        i = 0;
        length = 0;
        mark.clear();
        return this;
    }

    public boolean isEod() {
        return i >= length();
    }

    private void _append(byte[] tail) {
        if (buffer.length < length + tail.length) {
            byte[] dst = new byte[length + tail.length];
            System.arraycopy(buffer, 0, dst, 0, length);
            buffer = dst;
        }
        System.arraycopy(tail, 0, buffer, length, tail.length);
        length = length + tail.length;
    }


//    private byte[] _append(byte[] src, byte[] tail) {
//        byte[] dst = new byte[src.length + tail.length];
//        System.arraycopy(src, 0, dst, 0, src.length);
//        System.arraycopy(tail, 0, dst, src.length, tail.length);
//        return dst;
//    }
//
//    private byte[] _append(byte[] src, byte[] tail, int start, int length) {
//        length = tail.length > length ? length : tail.length;
//        byte[] dst = new byte[src.length + length];
//        System.arraycopy(src, 0, dst, 0, src.length);
//        System.arraycopy(tail, start, dst, src.length, length);
//        return dst;
//    }

    public byte[] getRest() {
        if (length - i > 0) {
            byte[] dst = new byte[length - i];
            System.arraycopy(buffer, i, dst, 0, length - i);
            return dst;
        } else {
            return new byte[]{};
        }
    }


}
