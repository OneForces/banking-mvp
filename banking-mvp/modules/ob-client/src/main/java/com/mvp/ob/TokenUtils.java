package com.mvp.ob;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Простейшее извлечение токена из JSON-строки. */
public class TokenUtils {
    private static final Pattern ANY_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    public static String extractAnyToken(String json) {
        if (json == null) return null;
        Matcher m = ANY_TOKEN.matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
