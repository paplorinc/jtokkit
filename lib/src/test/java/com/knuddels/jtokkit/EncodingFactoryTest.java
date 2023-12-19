package com.knuddels.jtokkit;

import com.knuddels.jtokkit.reference.Cl100kBaseTest;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.knuddels.jtokkit.EncodingFactory.compileRegex;
import static java.lang.Character.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.rangeClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EncodingFactoryTest {
    static final String originalRegex = GptBytePairEncodingOriginal.getEncoder().pattern.pattern();
    static final List<String> expectedOriginal = List.of("", "(?i:'s|'t|'re|'ve|'m|'ll|'d)", "[^\r\n\\p{L}\\p{N}]?\\p{L}+", "\\p{N}{1,3}", " ?[^\\s\\p{L}\\p{N}]+[\r\n]*", "\\s*[\r\n]+", "\\s+(?!\\S)", "\\s+");

    static final String currentRegex = "'(?:[sdmt]|ll|ve|re)|[^\r\n\\p{L}\\p{N}]?+\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]++[\r\n]*|\\s*[\r\n]|\\s+(?!\\S)|\\s+";
    static final List<String> currentRegexParts = List.of("", "'(?:[sdmt]|ll|ve|re)", "[^\r\n\\p{L}\\p{N}]?+\\p{L}+", "\\p{N}{1,3}", " ?[^\\s\\p{L}\\p{N}]++[\r\n]*", "\\s*[\r\n]", "\\s+(?!\\S)", "\\s+");

    static SortedMap<Integer, List<String>> getEncounters(String text, List<String> currentRegexParts, String currentRegex, boolean caseInsensitive) {
        assert currentRegexParts.stream().skip(1).collect(joining("|")).equals(currentRegex) : "Regex mismatch";
        var actualFinalRegex = currentRegexParts.stream().skip(1).map(x -> "(" + x + ")").collect(joining("|"));
        var actualPattern = compileRegex(actualFinalRegex, caseInsensitive);

        var encounters = getEncounters(text, actualPattern);

        // TODO try pre-splitting to enable parallel processing
        if (false) {
            var encounters2 = new TreeMap<Integer, List<String>>();
            Pattern.compile("(?<=\\s)(?=\\p{L}|\\p{N})").splitAsStream(text)
                    .forEach(match -> {
                        var encounters1 = getEncounters(match, actualPattern);
                        encounters2.putAll(encounters1);
                    });
            assert Objects.equals(encounters, encounters2) : "Encounters mismatch";
        }

        return encounters;
    }

    public static TreeMap<Integer, List<String>> getEncounters(String text, Pattern actualPattern) {
        var encounters = new TreeMap<Integer, List<String>>();
        for (var matcher = actualPattern.matcher(text); matcher.find(); ) {
            var match = matcher.group(0);
            @SuppressWarnings("OptionalGetWithoutIsPresent")
            var index = rangeClosed(1, matcher.groupCount())
                    .filter(i -> matcher.group(i) != null).findFirst()
                    .getAsInt();
            assert Objects.equals(match, matcher.group(index)) : "Mismatch between match and group " + index + " for text: " + text;
            // printExtraInfo(text, currentRegexParts, encounters, index, matcher);
            encounters.computeIfAbsent(index, k -> new ArrayList<>()).add(match);
        }
        return encounters;
    }

    private static void printExtraInfo(String text, List<String> currentRegexParts, Map<Integer, List<String>> encounters, int index, Matcher matcher) {
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
            System.out.println("Next chars after: `" + matcher.group() + "` for index " + index + ", pattern: `" + currentRegexParts.get(index) + "` is  + `" + c + "`");
        }
    }

    private static Map<String, List<String>> compareEncounters(String text, List<String> currentRegexParts, String currentRegex) {
        var actual = getEncounters(text, currentRegexParts, currentRegex, true);
        var expected = getEncounters(text, expectedOriginal, originalRegex, false);
        if (!Objects.equals(expected, actual)) {
            System.out.println("Expected: " + expected);
            System.out.println("Actual: " + actual);
            throw new AssertionError("Expected and actual encounters do not match");
        }

        var result = new LinkedHashMap<String, List<String>>();
        actual.forEach((index, match) -> result.put(currentRegexParts.get(index), match));
        return result;
    }

    private static String permuteText(String text) {
        var random = ThreadLocalRandom.current();
        var modifiedText = new StringBuilder(text);

        var whitespaceChars = getWhitespaces();

        for (var i = 0; i < modifiedText.length() - 1; i++) {
            if (random.nextInt(5) == 0) {
                // Randomly flip case whitespaces
                if (isWhitespace(modifiedText.charAt(i))) {
                    var newWhitespace = whitespaceChars.get(random.nextInt(whitespaceChars.size()));
                    for (var j = 0; j < newWhitespace.length(); j++) {
                        modifiedText.setCharAt(i + j, newWhitespace.charAt(j));
                    }
                }
            }

            // Randomly flip case
            if (random.nextInt(10) == 0) {
                var ch = random.nextBoolean() ?
                        toUpperCase(modifiedText.charAt(i)) :
                        toLowerCase(modifiedText.charAt(i));
                modifiedText.setCharAt(i, ch);
            }

            // Randomly insert Unicode characters
            if (random.nextInt(20) == 0) {
                var newChars = toChars(random.nextInt(MAX_CODE_POINT + 1));
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
        for (var c = MIN_VALUE; c < MAX_VALUE; c++) {
            if (isWhitespace(c)) {
                whitespaceChars.add(String.valueOf(c));
            }
        }
        whitespaceChars.addAll(List.of(" ", "\n", "\u000B", "\u000C", "\r", "\u0085", "\u2028", "\u2029"));
        return new ArrayList<>(whitespaceChars);
    }

    static String normalizeStringForTesting(String testString) {
        return testString
                .replaceAll("\\r", "\\\\r")
                .replaceAll("\\n", "\\\\n")
                .replaceAll(" ", "␣");
    }

    @Test
    void oldRegexMatchesTheSameWayAsTheOptimizedOne() throws Exception {
//        var collected = new LinkedHashMap<String, List<String>>();
//        currentRegexParts.stream().skip(1).forEach(x -> collected.put(x, new ArrayList<>()));
//        for (var text : TEXTS) {
//            compareEncounters(text, currentRegexParts, currentRegex)
//                    .forEach((key, value) -> collected.get(key).addAll(value));
//
//            var modifiedText = permuteText(text);
//            compareEncounters(modifiedText, currentRegexParts, currentRegex)
//                    .forEach((key, value) -> collected.get(key).addAll(value));
//        }
//
//        var groupedResults = collected.entrySet().stream()
//                .collect(toMap(
//                        Map.Entry::getKey,
//                        entry -> entry.getValue().stream().collect(groupingBy(String::toLowerCase, LinkedHashMap::new, counting())),
//                        (e1, e2) -> e1,
//                        LinkedHashMap::new
//                ));
//
//        System.out.println(groupedResults.size());

        var encounters = getEncounters("I'm:  0\n", currentRegexParts, currentRegex, true);
//        System.out.println(encounters);
        assertEquals(7, encounters.size());

        Map<String, SortedMap<Integer, List<String>>> completeLines = new TreeMap<>();
        for (var text : Cl100kBaseTest.getTexts("../")) {
//            text.lines().forEach(line -> {
//                var t = line + "\n";
            var actual = getEncounters(text, currentRegexParts, currentRegex, true);
            if (actual.size() == 7) {
                completeLines.put(text, actual);
            }
//            });
        }
//        System.out.println(completeLines.entrySet());
    }

    @Test
    public void testParser() {
        var testStrings = new ArrayList<>(List.of(
                "\n",
                " ",
                "a : b",
                "  a",
                "\n \n ",
                "\n \n",
                "\n ",
                "\n \n!",
                "\n \n   ",
                "\n  !",
                "\n A",
                "  \n\r  \r\n  \r \n  A\nA \n A",
                ",\n\n",
                " ***\n\n\n\n",

                "   !",
                "   A",
                "   0",
                "   *",

                "   \n!",
                "   \nA",
                "   \n0",
                "   \n*",

                "   \n !",
                "   \n A",
                "   \n 0",
                "   \n *",

                "I paid $123,456 to 9876543210 people!",
                "Unicode snowman: ☃️",
                "I'm:  0\n",
                "We'll meet at 3 o'clock.",
                "Hello, world! It's a beautiful day...",
                "In 2023, I'll be 25 years old.",
                "Hello \n\n World  !",
                " It's 2:30pm;\n\n\n\nlet's eat, sleep , and code!",
                "'Thank God, here it is.' But when we took up the trunk...",
                "user@example.com",
                "this is a 'quoted' word",
                "　　a",
                "'ſ",
                "'ſ\uD84F\uDDB8\uD84C\uDD2CƘ淚",
                "\uD83D\uDE29\n",
                "03½",
                "* \u05E2"
        ));
        testStrings.addAll(Cl100kBaseTest.getTexts("../"));

        var originalPattern = GptBytePairEncodingOriginal.getEncoder().pattern;
        IntStream.range(0, testStrings.size()).forEachOrdered(i -> {
            String testString = testStrings.get(i);
            System.out.println("Validating #" + i + ": `" + normalizeStringForTesting(testString.substring(0, Math.min(100, testString.length()))) + (testString.length() > 100 ? "..." : "") + "`");

            List<String> expected = matches(testString, originalPattern);
//            var normalizedExpected = expected.stream().map(EncodingFactoryTest::normalizeStringForTesting).toList();
//            System.out.println("Expected: " + normalizedExpected);

            List<String> actual = new ArrayList<>();
            Cl100kParser.split(testString, utf8Bytes -> {
                assert !utf8Bytes.isEmpty();
                actual.add(new String(utf8Bytes.toByteArray(), UTF_8));
                return false;
            });
//            var encounters = normalizeStringForTesting(getEncounters(testString, currentRegexParts, currentRegex, true).toString());
//            assertEquals(normalizedExpected, actual.stream().map(EncodingFactoryTest::normalizeStringForTesting).toList(), encounters);
            assertEquals(expected, actual);
        });
    }

    private List<String> matches(String input, Pattern pattern) {
        List<String> tokens = new ArrayList<>();
        for (Matcher matcher = pattern.matcher(input); matcher.find(); ) {
            var group = matcher.group();
            tokens.add(group);
        }
        return tokens;
    }
}