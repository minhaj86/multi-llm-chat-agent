package com.example.aichat.rag.infrastructure.extractor;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Decodes text files: honours UTF-8/UTF-16 byte order marks, prefers strict UTF-8 and falls back to Windows-1252
 * (a superset of ISO-8859-1) for legacy files. Line endings are normalised to {@code \n}.
 */
final class TextDecoding {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private TextDecoding() {
    }

    static String decode(byte[] bytes) {
        String text;
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        } else if (startsWith(bytes, 0xFE, 0xFF)) {
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        } else if (startsWith(bytes, 0xFF, 0xFE)) {
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        } else {
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                text = new String(bytes, WINDOWS_1252);
            }
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "");
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
