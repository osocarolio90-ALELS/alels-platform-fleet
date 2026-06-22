package com.alels.gateway.service;

import java.io.InputStream;
import java.security.MessageDigest;

public class DictionaryChecksumUtil {

    private DictionaryChecksumUtil() {
    }

    public static String sha256FromResource(String resourcePath) {

        try (
                InputStream inputStream =
                        DictionaryChecksumUtil.class
                                .getClassLoader()
                                .getResourceAsStream(resourcePath)
        ) {
            if (inputStream == null) {
                return null;
            }

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hash =
                    digest.digest();

            StringBuilder hex =
                    new StringBuilder();

            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (Exception e) {
            System.err.println(
                    "[DICT CHECKSUM ERROR] resource="
                            + resourcePath
                            + " error="
                            + e.getMessage()
            );

            return null;
        }
    }
}