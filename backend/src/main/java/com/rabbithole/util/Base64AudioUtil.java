package com.rabbithole.util;

public class Base64AudioUtil {

    public static byte[] decodeBase64Audio(String base64) {
        return java.util.Base64.getDecoder().decode(base64);
    }

    public static String encodeToBase64(byte[] audio) {
        return java.util.Base64.getEncoder().encodeToString(audio);
    }
}
