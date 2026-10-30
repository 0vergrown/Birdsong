package dev.overgrown.birdsong.speech;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PhraseMatcher {
    public static final PhraseMatcher EMPTY = new PhraseMatcher(new Phrase[0]);

    private static final int FUZZY_MIN_LENGTH = 5;
    private static final float PHONETIC_WEIGHT = 0.5F;

    private record Phrase(String text, String key, int words, @Nullable String soundex) {}

    public record Transcript(String raw, String[] words) {}

    private final Phrase[] phrases;

    private PhraseMatcher(Phrase[] phrases) {
        this.phrases = phrases;
    }

    public static PhraseMatcher of(Optional<String> message, List<String> messages) {
        List<Phrase> built = new ArrayList<>(messages.size() + 1);
        message.ifPresent(text -> add(built, text));
        for (String text : messages) add(built, text);
        return built.isEmpty() ? EMPTY : new PhraseMatcher(built.toArray(new Phrase[0]));
    }

    private static void add(List<Phrase> out, String text) {
        String[] words = words(text);
        if (words.length == 0) return;
        String key = String.join("", words);
        out.add(new Phrase(text, key, words.length, soundex(key)));
    }

    public static Transcript transcript(String text) {
        return new Transcript(text, words(text));
    }

    public boolean isEmpty() {
        return phrases.length == 0;
    }

    public @Nullable String match(Transcript transcript, float tolerance, boolean fuzzy) {
        String[] words = transcript.words();
        if (words.length == 0) return null;
        StringBuilder window = null;
        for (Phrase phrase : phrases) {
            if (compact(words, phrase.key())) return phrase.text();
            if (!fuzzy || tolerance <= 0.0F || phrase.key().length() < FUZZY_MIN_LENGTH) continue;
            if (window == null) window = new StringBuilder(32);
            if (near(words, phrase, tolerance, window)) return phrase.text();
        }
        return null;
    }

    private static boolean compact(String[] words, String key) {
        int keyLength = key.length();
        for (int i = 0; i < words.length; i++) {
            int offset = 0;
            for (int j = i; j < words.length; j++) {
                String word = words[j];
                if (offset + word.length() > keyLength || !key.startsWith(word, offset)) break;
                offset += word.length();
                if (offset == keyLength) return true;
            }
        }
        return false;
    }

    private static boolean near(String[] words, Phrase phrase, float tolerance, StringBuilder window) {
        String key = phrase.key();
        int maxWords = phrase.words() + 1;
        for (int i = 0; i < words.length; i++) {
            window.setLength(0);
            for (int j = i; j < words.length && j - i < maxWords; j++) {
                window.append(words[j]);
                int length = window.length();
                int longest = Math.max(length, key.length());
                int allowed = Math.max(2, longest / 4);
                if (length > key.length() + allowed) break;
                if (key.length() - length > allowed) continue;
                float score = (float) levenshtein(window, key) / longest;
                if (phrase.soundex() != null && phrase.soundex().equals(soundex(window))) score *= PHONETIC_WEIGHT;
                if (score <= tolerance) return true;
            }
        }
        return false;
    }

    private static String[] words(String text) {
        List<String> words = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        int start = -1;
        for (int i = 0, n = lower.length(); i <= n; i++) {
            boolean inside = i < n && Character.isLetterOrDigit(lower.charAt(i));
            if (inside && start < 0) {
                start = i;
            } else if (!inside && start >= 0) {
                words.add(lower.substring(start, i));
                start = -1;
            }
        }
        return words.toArray(new String[0]);
    }

    private static int levenshtein(CharSequence a, String b) {
        int n = b.length();
        int[] previous = new int[n + 1];
        int[] current = new int[n + 1];
        for (int j = 0; j <= n; j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[n];
    }

    private static @Nullable String soundex(CharSequence word) {
        int length = word.length();
        if (length == 0) return null;
        for (int i = 0; i < length; i++) {
            char c = word.charAt(i);
            if (c < 'a' || c > 'z') return null;
        }
        char[] code = {Character.toUpperCase(word.charAt(0)), '0', '0', '0'};
        int filled = 1;
        char last = soundexDigit(word.charAt(0));
        for (int i = 1; i < length && filled < 4; i++) {
            char c = word.charAt(i);
            char digit = soundexDigit(c);
            if (digit == '0') {
                if (c != 'h' && c != 'w') last = '0';
                continue;
            }
            if (digit != last) code[filled++] = digit;
            last = digit;
        }
        return new String(code);
    }

    private static char soundexDigit(char c) {
        return switch (c) {
            case 'b', 'f', 'p', 'v' -> '1';
            case 'c', 'g', 'j', 'k', 'q', 's', 'x', 'z' -> '2';
            case 'd', 't' -> '3';
            case 'l' -> '4';
            case 'm', 'n' -> '5';
            case 'r' -> '6';
            default -> '0';
        };
    }
}
