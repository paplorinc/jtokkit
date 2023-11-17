package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.knuddels.jtokkit.EncodingFactory.compileRegex;
import static com.knuddels.jtokkit.reference.Cl100kBaseTestTest.TEXTS;
import static java.lang.Character.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.rangeClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EncodingFactoryTest {
    static final String originalRegex = "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+";
    static final List<String> expectedOriginal = List.of("", "(?i:'s|'t|'re|'ve|'m|'ll|'d)", "[^\\r\\n\\p{L}\\p{N}]?\\p{L}+", "\\p{N}{1,3}", " ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*", "\\s*[\\r\\n]+", "\\s+(?!\\S)", "\\s+");

    static final String currentRegex = "'(?:[sdmt]|ll|ve|re)|[^\\r\\n\\p{L}\\p{N}]?+\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]++[\\r\\n]*|\\s*[\\r\\n]|\\s+(?!\\S)|\\s+";
    static final List<String> currentRegexParts = List.of("", "'(?:[sdmt]|ll|ve|re)", "[^\\r\\n\\p{L}\\p{N}]?+\\p{L}+", "\\p{N}{1,3}", " ?[^\\s\\p{L}\\p{N}]++[\\r\\n]*", "\\s*[\\r\\n]", "\\s+(?!\\S)", "\\s+");

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
                .replaceAll(" ", "·");
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
        System.out.println(encounters);
        assertEquals(7, encounters.size());

        Map<String, SortedMap<Integer, List<String>>> completeLines = new TreeMap<>();
        for (var text : TEXTS) {
//            text.lines().forEach(line -> {
//                var t = line + "\n";
            var actual = getEncounters(text, currentRegexParts, currentRegex, true);
            if (actual.size() == 7) {
                completeLines.put(text, actual);
            }
//            });
        }
        System.out.println(completeLines.entrySet());
    }

    @Test
    public void testParser() {
        var testStrings = new ArrayList<>(List.of(
                "\n",
                " ",
                "  a",
                "\n \n ",
                "\n \n",
                "\n ",
                "\n \n!",
                "\n \n   ",
                "\n  !",

                "   !",
                "   A",
                "   0",

                "   \n!",
                "   \nA",
                "   \n0",

                "   \n !",
                "   \n A",
                "   \n 0",

                "Unicode snowman: ☃️",
                "I'm:  0\n",
                "We'll meet at 3 o'clock.",
                "Hello, world! It's a beautiful day...",
                "In 2023, I'll be 25 years old.",
                "Hello \n\n World  !",
                " It's 2:30pm;\n\n\n\nlet's eat, sleep , and code!",
                "'Thank God, here it is.' But when we took up the trunk..."
        ));
        testStrings.addAll(TEXTS);
        // TODO NBSP?

        var pattern = compileRegex(originalRegex, false);
        for (String testString : testStrings) {
            System.out.println("Matching: `" + normalizeStringForTesting(testString.substring(0, Math.min(100, testString.length()))) + "`...");
            List<String> expected = matches(testString, pattern);
            List<String> actual = Parser.parse(testString);

//            assertEquals(expected.stream().map(EncodingFactoryTest::normalizeString).toList(), actual.stream().map(EncodingFactoryTest::normalizeString).toList());
            assertEquals(expected, actual);
        }
    }

    private List<String> matches(String input, Pattern pattern) {
        List<String> tokens = new ArrayList<>();
        for (Matcher matcher = pattern.matcher(input); matcher.find(); ) {
            var group = matcher.group();
            tokens.add(group);
        }
        return tokens;
    }

    public static class Parser {

        public static final String SDTM = "sdtmSDTM";

        public static List<String> parse(String input) {
            List<String> tokens = new ArrayList<>();
            StringBuilder currentToken = new StringBuilder();

            for (int index = 0; index < input.length(); ) {
                char c0 = input.charAt(index);

                // 1) `'(?:[sdtm]|ll|ve|re)` - contractions, such as the suffixes of `he's`, `I'd`, `'tis`, `I'm`, `you'll`, `we've`, `they're`
                if (isShortContraction(input, c0, index)) {
                    currentToken.append(c0).append(input.charAt(index + 1));
                } else if (isLongContraction(input, c0, index)) {
                    currentToken.append(c0).append(input.charAt(index + 1)).append(input.charAt(index + 2));
                } else if (isLetter(c0) ||
                        (!isLetterOrDigit(c0) && "\r\n".indexOf(c0) < 0 && (index + 1 >= input.length() || isLetter(input.charAt(index + 1))))) {
                    // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`
                    currentToken.append(c0);
                    int j = index + 1;

                    while (j < input.length()) {
                        c0 = input.charAt(j);
                        if (!isLetter(c0)) {
                            break;
                        }
                        currentToken.append(c0);
                        j++;
                    }
                } else if (isDigit(c0)) {
                    // 3) `\p{N}{1,3}` - numbers, such as `4`, `235`
                    currentToken.append(c0);
                    int j = index + 1;
                    if (j < input.length()) {
                        var c1 = input.charAt(j);
                        if (isDigit(c1)) {
                            currentToken.append(c1);
                            j++;
                            if (j < input.length()) {
                                var c2 = input.charAt(j);
                                if (isDigit(c2)) {
                                    currentToken.append(c2);
                                }
                            }
                        }
                    }
                } else if ((!isWhitespace(c0) && !isLetterOrDigit(c0)) ||
                        (index + 1 >= input.length() || (!isWhitespace(input.charAt(index + 1)) && !isLetterOrDigit(input.charAt(index + 1))))) {
                    // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, ` .`, `"`
                    currentToken.append(c0);
                    var j = index + 1;
                    while (j < input.length()) {
                        c0 = input.charAt(j);
                        if (isWhitespace(c0) || isLetterOrDigit(c0)) {
                            break;
                        }
                        currentToken.append(c0);
                        j++;
                    }

                    while (j < input.length()) {
                        c0 = input.charAt(j);
                        if (c0 != '\r' && c0 != '\n') {
                            break;
                        }

                        currentToken.append(c0);
                        j++;
                    }
                } else {
                    // 5) `\s*[\r\n]+` - line endings such as `\r\n    \r\n`
                    // 6) `\s+(?!\S)` - whitespaces such as `               ` or ` `
                    // 7) `\s+` - unmatched remaining spaces, such as ` `
                    assert isWhitespace(c0);
                    int j = index;
                    do {
                        c0 = input.charAt(j);
                        if (!isWhitespace(c0)) {
                            break;
                        }
                        currentToken.append(c0);
                        j++;
                    } while (j < input.length());

                    int lastNewLineIndex = Math.max(currentToken.lastIndexOf("\r"), currentToken.lastIndexOf("\n"));
                    if (lastNewLineIndex >= 0) {
                        tokens.add(currentToken.substring(0, lastNewLineIndex + 1));
                        currentToken.delete(0, lastNewLineIndex + 1);
                        index += lastNewLineIndex + 1;
                        j = index;
                    }

                    if (!currentToken.isEmpty()) {
                        if ((j < input.length() && !isWhitespace(c0))
                                && (lastNewLineIndex >= 0 || currentToken.length() > 1)) {
                            currentToken.setLength(currentToken.length() - 1);
                        }
                    }
                }

                if (!currentToken.isEmpty()) {
                    index += currentToken.length();
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            }
            return tokens;
        }

        private static boolean isShortContraction(String input, char c0, int index) {
            if (c0 != '\'' || index + 1 >= input.length()) {
                return false;
            }
            var c1 = input.charAt(index + 1);
            return SDTM.indexOf(c1) >= 0;
        }

        private static boolean isLongContraction(String input, char c0, int index) {
            if (c0 != '\'' || index + 2 >= input.length()) {
                return false;
            }
            var c1 = toLowerCase(input.charAt(index + 1));
            var c2 = toLowerCase(input.charAt(index + 2));
            return ((c1 == 'l' && c2 == 'l') || (c1 == 'v' && c2 == 'e') || (c1 == 'r' && c2 == 'e'));
        }
    }
}