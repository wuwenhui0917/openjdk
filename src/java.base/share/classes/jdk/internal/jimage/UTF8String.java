/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;

public final class UTF8String implements CharSequence {
    // Same as StandardCharsets.UTF_8 without loading all of the standard charsets
    static final Charset UTF_8 = Charset.forName("UTF-8");

    public static final int NOT_FOUND = -1;
    public static final int HASH_MULTIPLIER = 0x01000193;
    public static final UTF8String EMPTY_STRING = new UTF8String("");
    public static final UTF8String SLASH_STRING = new UTF8String("/");
    public static final UTF8String DOT_STRING = new UTF8String(".");

    // TODO This strings are implementation specific and should be defined elsewhere.
    public static final UTF8String MODULES_STRING = new UTF8String("/modules");
    public static final UTF8String PACKAGES_STRING = new UTF8String("/packages");

    final byte[] bytes;
    final int offset;
    final int count;
    int hashcode;

    public UTF8String(byte[] bytes, int offset, int count) {
        if (offset < 0 || count < 0 || (offset + count) > bytes.length) {
            throw new IndexOutOfBoundsException("offset/count out of range");
        }
        this.bytes = bytes;
        this.offset = offset;
        this.count = count;
        this.hashcode = -1;
    }

    public UTF8String(byte[] bytes, int offset) {
        this(bytes, offset, bytes.length - offset);
    }

    public UTF8String(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public UTF8String(String string) {
        this(stringToBytes(string));
    }

    @Override
    public int length() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int byteAt(int index) {
        return bytes[offset + index] & 0xFF;
    }

    public UTF8String concat(UTF8String s) {
        int total = count + s.count;
        byte[] combined = new byte[total];
        System.arraycopy(bytes, offset, combined, 0, count);
        System.arraycopy(s.bytes, s.offset, combined, count, s.count);

        return new UTF8String(combined, 0, total);
    }

    public UTF8String concat(UTF8String... s) {
        int total = count;

        for (UTF8String i : s) {
            total += i.count;
        }

        byte[] combined = new byte[total];
        System.arraycopy(bytes, offset, combined, 0, count);
        int next = count;

        for (UTF8String i : s) {
            System.arraycopy(i.bytes, i.offset, combined, next, i.count);
            next += i.count;
        }

        return new UTF8String(combined, 0, total);
    }

    public UTF8String substring(int offset) {
        return substring(offset, this.count - offset);
    }

    public UTF8String substring(int offset, int count) {
        int newOffset = this.offset + offset;
        return new UTF8String(bytes, newOffset, count);
    }

    public UTF8String trimToSize() {
        return offset == 0 && bytes.length == count ? this :
               new UTF8String(Arrays.copyOfRange(bytes, offset, offset + count));
    }

    public int indexOf(int ch) {
        return indexOf(ch, 0);
    }

    public int indexOf(int ch, int start) {
        for (int i = Math.max(start, 0); i < count; i++) {
            if (byteAt(i) == ch) {
                return i;
            }
        }

        return NOT_FOUND;
    }

    public int lastIndexOf(int ch) {
        return lastIndexOf(ch, count - 1);
    }

    public int lastIndexOf(int ch, int start) {
        for (int i = Math.min(start, count); i > 0; i--) {
            if (byteAt(i) == ch) {
                return i;
            }
        }

        return NOT_FOUND;
    }

    public void writeTo(ImageStream buffer) {
        buffer.put(bytes, offset, count);
    }

    public static int hashCode(int seed, byte[] bytes, int offset, int count) {
        for (int i = offset, limit = offset + count; i < limit; i++) {
            seed = (seed * HASH_MULTIPLIER) ^ (bytes[i] & 0xFF);
        }

        return seed & 0x7FFFFFFF;
    }

    public int hashCode(int seed) {
        return hashCode(seed, bytes, offset, count);
    }

    @Override
    public int hashCode() {
        if (hashcode < 0) {
            hashcode = hashCode(HASH_MULTIPLIER, bytes, offset, count);
        }

        return hashcode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        return equals(this, (UTF8String)obj);
    }

    public static boolean equals(UTF8String a, UTF8String b) {
        if (a == b) {
            return true;
        }

        int count = a.count;

        if (count != b.count) {
            return false;
        }

        byte[] aBytes = a.bytes;
        byte[] bBytes = b.bytes;
        int aOffset = a.offset;
        int bOffset = b.offset;

        for (int i = 0; i < count; i++) {
            if (aBytes[aOffset + i] != bBytes[bOffset + i]) {
                return false;
            }
        }

        return true;
    }

    public byte[] getBytesCopy() {
        return Arrays.copyOfRange(bytes, offset, offset + count);
    }

    byte[] getBytes() {
        if (offset != 0 || bytes.length != count) {
            return Arrays.copyOfRange(bytes, offset, offset + count);
        }

        return bytes;
    }

    /**
     * Convert the string bytes into Modified UTF-8 encoding (as defined in
     * <code>java.io.DataInput</code>
     * @param string
     * @return bytes encoded into modified UTF-8
     */
    private static byte[] stringToBytes(String string) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream ss = new DataOutputStream(bos);
            ss.writeUTF(string);
            byte[] content = bos.toByteArray();
            // first 2 items are length;
            if(content.length <= 2) {
                return new byte[0];
            }
            return Arrays.copyOfRange(content, 2, content.length);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String toString() {
        ByteBuffer buffer = ByteBuffer.allocate(count+2);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short)count);
        buffer.put(bytes, offset, count);
        ByteArrayInputStream stream = new ByteArrayInputStream(buffer.array());
        DataInputStream in = new DataInputStream(stream);
        try {
            return in.readUTF();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public char charAt(int index) {
        int ch = byteAt(index);

        return (ch & 0x80) == 0 ? (char)ch : '\0';
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return (CharSequence)substring(start, end - start);
    }
}
