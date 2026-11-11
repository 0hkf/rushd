package com.rushd.service;

import java.util.Locale;
import java.util.Set;

/** Comparison keys only. Never changes stored locations or guesses equivalent Arabic letters. */
public final class PropertyLocationComparison {
    private static final Set<String> MAKKAH_NAMES = Set.of("مكة", "مكة المكرمة", "makkah");

    private PropertyLocationComparison() {}

    public static Set<String> makkahNames() {
        return MAKKAH_NAMES;
    }

    public static boolean isMakkah(String city) {
        String key = whitespaceKey(city);
        return key != null && MAKKAH_NAMES.contains(key.toLowerCase(Locale.ROOT));
    }

    public static String districtKey(String district) {
        String key = whitespaceKey(district);
        if (key == null || key.equals("حي")) {
            return null;
        }
        String districtName = key.startsWith("حي ") ? key.substring("حي ".length()) : key;
        return districtName.equals("حي") ? null : districtName;
    }

    private static String whitespaceKey(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        boolean separator = false;
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            offset += Character.charCount(point);
            if (Character.isWhitespace(point) || Character.isSpaceChar(point)) {
                separator = result.length() > 0;
            } else {
                if (separator) {
                    result.append(' ');
                }
                result.appendCodePoint(point);
                separator = false;
            }
        }
        return result.length() == 0 ? null : result.toString();
    }
}
