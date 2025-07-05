package GTNHNightlyUpdater.Utils;

import java.util.regex.Pattern;

public class NameSanitizer {
    private static final Pattern INVALID_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern TRAILING_DOTS_SPACES = Pattern.compile("[\\.\\s]+$");

    public static String sanitizeFileNameWithExtension(String input, String replacement) {
        if (input == null || input.isEmpty()) return replacement;
        int lastDot = input.lastIndexOf('.');
        String name = (lastDot > 0 && lastDot < input.length() - 1) ? sanitizeComponent(input.substring(0, lastDot), replacement) + input.substring(lastDot) : sanitizeComponent(input, replacement);
        return name;
    }

    public static String sanitizeComponent(String name, String replacement) {
        if (name == null || name.isEmpty()) return replacement;
        name = INVALID_CHARS.matcher(name).replaceAll(replacement);
        name = TRAILING_DOTS_SPACES.matcher(name).replaceAll(replacement);
        return name.isEmpty() ? replacement : name;
    }
}
