package com.jih10157.voicemodmaker.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class EscartemFile implements Closeable {

    private final FileChannel channel;
    private final ByteBuffer byteBuffer4 = ByteBuffer.allocate(4);
    private final ByteBuffer byteBuffer32 = ByteBuffer.allocate(32);

    public EscartemFile(Path path) throws IOException {
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
        this.byteBuffer4.order(ByteOrder.LITTLE_ENDIAN);
        this.byteBuffer32.order(ByteOrder.LITTLE_ENDIAN);
    }

    public Map<String, String> getMapped() throws IOException {
        this.channel.position(0);
        if (!Arrays.equals(_read(4), getBytes("ESFM"))) {
            close();
            throw new RuntimeException("매핑 파일이 유효하지 않습니다.");
        }
        if (!Arrays.equals(_read(4), new byte[]{0, 0, 0x56, 0x31})) {
            close();
            throw new RuntimeException("매핑 파일의 버전이 유효하지 않습니다.");
        }
        skip(2);
        int count = readInt8();
        for (int i = 0; i < count; i++) {
            readString(readInt8());
        }

        count = readInt32();

        Map<String, Integer> map = new LinkedHashMap<>();

        for (int i = 0; i < count; i++) {
            String key = readString(readInt8());
            skip(1);
            int dataOffset = readInt32();
            map.put(key, dataOffset);
        }

        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            this.channel.position(entry.getValue());
            String data = readString(readInt8());
            result.put(entry.getKey(), data);
        }

        return result;
    }
    
    private void skip(int length) throws IOException {
        this.channel.position(this.channel.position() + length);
    }

    private int readInt8() throws IOException {
        return _read(1)[0];
    }

    private int readInt32() throws IOException {
        this.byteBuffer4.clear();
        int bytes = this.channel.read(this.byteBuffer4);
        if (bytes <= 0) {
            return -1;
        }
        this.byteBuffer4.flip();
        return this.byteBuffer4.getInt();
    }

    private String readString(int length) throws IOException {
        return new String(_read(length), StandardCharsets.UTF_8);
    }

    private byte[] _read(int length) throws IOException {
        if (length <= 4) {
            this.byteBuffer4.clear();
            this.byteBuffer4.limit(length);
            int bytes = this.channel.read(this.byteBuffer4);
            if (bytes <= 0) {
                return new byte[0];
            }
            this.byteBuffer4.flip();
            byte[] b = new byte[bytes];
            this.byteBuffer4.get(b);
            return b;
        } else if (length <= 32) {
            this.byteBuffer32.clear();
            this.byteBuffer32.limit(length);
            int bytes = this.channel.read(this.byteBuffer32);
            if (bytes <= 0) {
                return new byte[0];
            }
            this.byteBuffer32.flip();
            byte[] b = new byte[bytes];
            this.byteBuffer32.get(b);
            return b;
        } else {
            ByteArrayOutputStream stream = new ByteArrayOutputStream(length);
            int count = length / 32 + 1;
            for (int i = 0; i < count; i++) {
                this.byteBuffer32.clear();
                if (count - 1 == i) {
                    this.byteBuffer32.limit(length % 32);
                }

                int bytes = this.channel.read(this.byteBuffer32);
                if (bytes <= 0) {
                    break;
                }
                this.byteBuffer32.flip();
                byte[] b = new byte[bytes];
                this.byteBuffer32.get(b);
                stream.write(b);
            }
            return stream.toByteArray();
        }
    }

    @Override
    public void close() throws IOException {
        this.channel.close();
    }

    public static void main(String[] args) throws IOException {
        EscartemFile escapedFile = new EscartemFile(Paths.get("data", "latest.map"));
        escapedFile.getMapped();

        escapedFile.close();
    }

    private static byte[] getBytes(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }
}
