package com.itsm.domain;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The kinds of file that can be attached to an incident: screenshots, PDFs
 * and plain-text logs. The kind is worked out from the file's first bytes,
 * never from the name or the type the browser claims, so a script renamed to
 * "photo.png" is refused. Formats that can carry scripts (SVG, HTML) are
 * deliberately not on the list.
 */
public enum FileKind {

    PNG("image/png", "png", true),
    JPEG("image/jpeg", "jpg", true),
    GIF("image/gif", "gif", true),
    WEBP("image/webp", "webp", true),
    PDF("application/pdf", "pdf", false),
    TEXT("text/plain;charset=UTF-8", "txt", false);

    /** Text files are only accepted with one of these extensions. */
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "log", "csv");

    private final String mediaType;
    private final String extension;
    private final boolean image;

    FileKind(String mediaType, String extension, boolean image) {
        this.mediaType = mediaType;
        this.extension = extension;
        this.image = image;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getExtension() {
        return extension;
    }

    public boolean isImage() {
        return image;
    }

    /** The kind of the file, from its content; empty if it is not one that may be attached. */
    public static Optional<FileKind> detect(byte[] data, String fileName) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        if (startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(data, 0, 'G', 'I', 'F', '8', '7', 'a') || startsWith(data, 0, 'G', 'I', 'F', '8', '9', 'a')) {
            return Optional.of(GIF);
        }
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        if (startsWith(data, 0, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        if (TEXT_EXTENSIONS.contains(extensionOf(fileName)) && isPlainText(data)) {
            return Optional.of(TEXT);
        }
        return Optional.empty();
    }

    /** The extension of a file name in lower case, e.g. "log"; empty if there is none. */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /** Valid UTF-8 without control characters other than tab, line breaks and form feed. */
    private static boolean isPlainText(byte[] data) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
            return text.chars().noneMatch(c -> c < 0x20 && c != '\t' && c != '\n' && c != '\r' && c != '\f');
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
