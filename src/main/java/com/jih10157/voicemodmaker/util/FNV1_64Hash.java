package com.jih10157.voicemodmaker.util;

import java.nio.charset.StandardCharsets;

public class FNV1_64Hash {

    private static final long FNV1_64_INIT = 0xcbf29ce484222325L;
    private static final long FNV1_PRIME_64 = 1099511628211L;

    public static String fnv1_64(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        long hash = FNV1_64_INIT;

        for (byte b : bytes) {
            hash *= FNV1_PRIME_64;
            hash ^= (long) b & 0xff;
        }

        String str = Long.toHexString(hash);
        return padLeftZeros(str, 16);
    }

    public static void main(String[] args) {
        String input = "KOREAN\\VO_AQ\\VO_wriothesley\\vo_FDAQ106_9_wriothesley_07.wem".toLowerCase();
        String hash = fnv1_64(input);
        System.out.println("Input: " + input);
        System.out.println("FNV-1 64-bit Hash: " + hash);
    }

    public static String padLeftZeros(String inputString, int length) {
        if (inputString.length() >= length) {
            return inputString;
        }
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length - inputString.length()) {
            sb.append('0');
        }
        sb.append(inputString);

        return sb.toString();
    }
}