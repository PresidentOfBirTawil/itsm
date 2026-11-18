package com.quell.config;

/** Tiny helpers for writing JSON by hand where a whole JSON library would be overkill. */
public final class Json {

    private Json() {
    }

    /** A JSON string literal (with the quotes), or null. "<" is escaped so the text is also safe inside HTML. */
    public static String quote(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder json = new StringBuilder(text.length() + 2).append('"');
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                case '<' -> json.append("\\u003c");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        json.append("\\u%04x".formatted((int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }
}
