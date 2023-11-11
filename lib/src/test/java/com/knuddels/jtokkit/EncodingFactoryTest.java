package com.knuddels.jtokkit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;

import static com.knuddels.jtokkit.EncodingFactory.compileRegex;
import static com.knuddels.jtokkit.reference.Cl100kBaseTestTest.TEXTS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.*;
import static java.util.stream.IntStream.rangeClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EncodingFactoryTest {
    static final String originalRegex = "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+";
    static final List<String> expectedOriginal = List.of("", "(?i:'s|'t|'re|'ve|'m|'ll|'d)", "[^\\r\\n\\p{L}\\p{N}]?\\p{L}+", "\\p{N}{1,3}", " ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*", "\\s*[\\r\\n]+", "\\s+(?!\\S)", "\\s+");

    static SortedMap<Integer, List<String>> getEncounters(String text, List<String> actualRegexParts, String expectedRegex, boolean caseInsensitive) {
        assert actualRegexParts.stream().skip(1).collect(joining("|")).equals(expectedRegex) : "Regex mismatch";
        var actualFinalRegex = actualRegexParts.stream().skip(1).map(x -> "(" + x + ")").collect(joining("|"));
        var actualPattern = compileRegex(actualFinalRegex, caseInsensitive);
        var encounters = new TreeMap<Integer, List<String>>();

        for (var matcher = actualPattern.matcher(text); matcher.find(); ) {
            var match = matcher.group(0);
            @SuppressWarnings("OptionalGetWithoutIsPresent")
            var index = rangeClosed(1, matcher.groupCount())
                    .filter(i -> matcher.group(i) != null).findFirst()
                    .getAsInt();
            assert Objects.equals(match, matcher.group(index)) : "Mismatch between match and group " + index + " for text: " + text;
            // printExtraInfo(text, actualRegexParts, encounters, index, matcher);
            encounters.computeIfAbsent(index, k -> new ArrayList<>()).add(match);
        }
        return encounters;
    }

    private static void printExtraInfo(String text, List<String> actualRegexParts, Map<Integer, List<String>> encounters, int index, Matcher matcher) {
        if (!encounters.containsKey(index)) {
            var end = matcher.end();
            String c;
            if (end >= text.length()) {
                c = "<end>";
            } else if (end >= text.length() - 1) {
                c = text.charAt(end) + "<end>";
            } else {
                c = text.substring(end, end + 2);
            }
            System.out.println("Next chars after: `" + matcher.group() + "` for index " + index + ", pattern: `" + actualRegexParts.get(index) + "` is  + `" + c + "`");
        }
    }

    private static Map<String, List<String>> compareEncounters(String text, List<String> actualRegexParts, String expectedRegex) {
        var actual = getEncounters(text, actualRegexParts, expectedRegex, true);
        var expected = getEncounters(text, expectedOriginal, originalRegex, false);
        if (!Objects.equals(expected, actual)) {
            System.out.println("Expected: " + expected);
            System.out.println("Actual: " + actual);
            throw new AssertionError("Expected and actual encounters do not match");
        }

        var result = new LinkedHashMap<String, List<String>>();
        actual.forEach((index, match) -> result.put(actualRegexParts.get(index), match));
        return result;
    }

    private static String permuteText(String text) {
        var random = ThreadLocalRandom.current();
        var modifiedText = new StringBuilder(text);

        var whitespaceChars = getWhitespaces();

        for (var i = 0; i < modifiedText.length() - 1; i++) {
            if (random.nextInt(5) == 0) {
                // Randomly flip case whitespaces
                if (Character.isWhitespace(modifiedText.charAt(i))) {
                    var newWhitespace = whitespaceChars.get(random.nextInt(whitespaceChars.size()));
                    for (var j = 0; j < newWhitespace.length(); j++) {
                        modifiedText.setCharAt(i + j, newWhitespace.charAt(j));
                    }
                }
            }

            // Randomly flip case
            if (random.nextInt(10) == 0) {
                var ch = random.nextBoolean() ?
                        Character.toUpperCase(modifiedText.charAt(i)) :
                        Character.toLowerCase(modifiedText.charAt(i));
                modifiedText.setCharAt(i, ch);
            }

            // Randomly insert Unicode characters
            if (random.nextInt(20) == 0) {
                var newChars = Character.toChars(random.nextInt(Character.MAX_CODE_POINT + 1));
                for (var j = 0; j < newChars.length; j++) {
                    modifiedText.setCharAt(i + j, newChars[j]);
                }
            }
        }

        assert modifiedText.length() == text.length() : "Length mismatch for text: " + modifiedText.length() + " vs " + text.length();
        return modifiedText.toString();
    }

    private static List<String> getWhitespaces() {
        Set<String> whitespaceChars = new HashSet<>();
        for (var c = Character.MIN_VALUE; c < Character.MAX_VALUE; c++) {
            if (Character.isWhitespace(c)) {
                whitespaceChars.add(String.valueOf(c));
            }
        }
        whitespaceChars.addAll(List.of(" ", "\n", "\u000B", "\u000C", "\r", "\u0085", "\u2028", "\u2029"));
        return new ArrayList<>(whitespaceChars);
    }

    @Test
    void oldRegexMatchesTheSameWayAsTheOptimizedOne() throws Exception {
        var actualRegexParts = List.of("", "'(?:[sdtm]|ll|ve|re)", "[^\\r\\n\\p{L}\\p{N}]?+\\p{L}+", "\\p{N}{1,3}", " ?[^\\s\\p{L}\\p{N}]++[\\r\\n]*", "\\s*[\\r\\n]", "\\s+(?!\\S)", "\\s+");
        var expectedRegex = "'(?:[sdtm]|ll|ve|re)|[^\\r\\n\\p{L}\\p{N}]?+\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]++[\\r\\n]*|\\s*[\\r\\n]|\\s+(?!\\S)|\\s+";

        var collected = new LinkedHashMap<String, List<String>>();
        actualRegexParts.stream().skip(1).forEach(x -> collected.put(x, new ArrayList<>()));
        for (var text : TEXTS) {
            compareEncounters(text, actualRegexParts, expectedRegex)
                    .forEach((key, value) -> collected.get(key).addAll(value));

            var modifiedText = permuteText(text);
            compareEncounters(modifiedText, actualRegexParts, expectedRegex)
                    .forEach((key, value) -> collected.get(key).addAll(value));
        }

        var groupedResults = collected.entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().collect(groupingBy(String::toLowerCase, LinkedHashMap::new, counting())),
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        System.out.println(groupedResults.size());
    }

    @Disabled
    @Test
    void testCalculateByteLengthForIndividualCharacters() {
        for (var text : TEXTS) {
            var bytes = text.getBytes(UTF_8);

            var prev = 0;
            if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
                // Skip BOM if present
                prev += 3;
            }
            for (var i = 0; i < text.length(); i++) {
                var substring = text.substring(i, i + 1);
                var expectedLength = substring.getBytes(UTF_8).length;
                var actualLength = calculateByteLength(bytes, prev, prev + expectedLength);

                assertEquals(expectedLength, actualLength, "Byte length mismatch for character: `" + text.charAt(i) + "`");
                assertEquals(new String(bytes, prev, prev + expectedLength, UTF_8), substring, "Substring mismatch for character: `" + text.charAt(i) + "`");
                prev += actualLength;
            }
        }
    }

    int calculateByteLength(byte[] bytes, int start, int end) {
        var byteLength = 0;
        for (var i = start; i < end; i++) {
            if ((bytes[i] & 0x80) == 0) {
                byteLength += 1;
            } else if ((bytes[i] & 0xE0) == 0xC0) {
                byteLength += 2;
                i += 1;
            } else if ((bytes[i] & 0xF0) == 0xE0) {
                byteLength += 3;
                i += 2;
            } else if ((bytes[i] & 0xF8) == 0xF0) {
                byteLength += 4;
                i += 3;
            }
        }
        return byteLength;
    }
}