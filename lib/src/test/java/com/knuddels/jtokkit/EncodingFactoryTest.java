package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.knuddels.jtokkit.EncodingFactory.compileRegex;
import static com.knuddels.jtokkit.reference.Cl100kBaseTestTest.TEXTS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.rangeClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EncodingFactoryTest {
    static final String originalRegex = "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+";
    static final List<String> expectedOriginal = List.of("", "(?i:'s|'t|'re|'ve|'m|'ll|'d)", "[^\\r\\n\\p{L}\\p{N}]?\\p{L}+", "\\p{N}{1,3}", " ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*", "\\s*[\\r\\n]+", "\\s+(?!\\S)", "\\s+");

    static SortedMap<Integer, List<String>> getEncounters(String text, List<String> actualRegexParts, String expectedRegex, boolean caseInsensitive) {
        assert actualRegexParts.stream().skip(1).collect(joining("|")).equals(expectedRegex) : "Regex mismatch";
        var actualFinalRegex = actualRegexParts.stream().skip(1).map(x -> "(" + x + ")").collect(joining("|"));
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
        var actualRegexParts = List.of("", "'(?:[sdmt]|ll|ve|re)", "[^\\r\\n\\p{L}\\p{N}]?+\\p{L}+", "\\p{N}{1,3}", " ?[^\\s\\p{L}\\p{N}]++[\\r\\n]*", "\\s*[\\r\\n]", "\\s+(?!\\S)", "\\s+");
        var expectedRegex = "'(?:[sdmt]|ll|ve|re)|[^\\r\\n\\p{L}\\p{N}]?+\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]++[\\r\\n]*|\\s*[\\r\\n]|\\s+(?!\\S)|\\s+";

//        var collected = new LinkedHashMap<String, List<String>>();
//        actualRegexParts.stream().skip(1).forEach(x -> collected.put(x, new ArrayList<>()));
//        for (var text : TEXTS) {
//            compareEncounters(text, actualRegexParts, expectedRegex)
//                    .forEach((key, value) -> collected.get(key).addAll(value));
//
//            var modifiedText = permuteText(text);
//            compareEncounters(modifiedText, actualRegexParts, expectedRegex)
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

        var encounters = getEncounters("I'm:  0\n", actualRegexParts, expectedRegex, true);
        System.out.println(encounters);
        assertEquals(7, encounters.size());

        Map<String, SortedMap<Integer, List<String>>> completeLines = new TreeMap<>();
        for (var text : TEXTS) {
//            text.lines().forEach(line -> {
//                var t = line + "\n";
            var actual = getEncounters(text, actualRegexParts, expectedRegex, true);
            if (actual.size() == 7) {
                completeLines.put(text, actual);
            }
//            });
        }
        System.out.println(completeLines.entrySet());
    }

    @Test
    public void testParser() {
        List<String> testStrings = Arrays.asList(
                "Unicode snowman: ☃️",
                "I'm:  0\n",
                "We'll meet at 3 o'clock.",
                "Hello, world! It's a beautiful day...",
                "In 2023, I'll be 25 years old.",
                "Hello \n\n World  !",
                " It's 2:30pm;\n\n\n\nlet's eat, sleep , and code! \n \n \t"
        );
        var encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
        for (String testString : testStrings) {
            List<String> expected = matches(testString, originalRegex);
            var collected = encoding.encode(testString).primitiveStream().mapToObj(token -> new String(encoding.decodeToken(token), UTF_8)).toList();
            // TODO assertEquals(expected, collected);

            List<String> result = new Parser().parse(testString);

            assertEquals(expected, result, "Parsed result does not match expected for: " + testString);
            System.out.println("`" + testString + "` matches!");
        }
    }

    private List<String> matches(String input, String regex) {
        List<String> tokens = new ArrayList<>();
        for (Matcher matcher = compileRegex(regex, false).matcher(input); matcher.find(); ) {
            var group = matcher.group();
            assert input.contains(group);
            tokens.add(group);
        }
        return tokens;
    }

    public static class Parser {
        private static void addMatch(List<String> tokens, StringBuilder currentToken) {
            tokens.add(currentToken.toString());
            currentToken.setLength(0);
        }

        public List<String> parse(String input) {
            List<String> tokens = new ArrayList<>();
            StringBuilder currentToken = new StringBuilder();

            for (int i = 0; i < input.length(); ) {
                char c0 = input.charAt(i);

                if (c0 == '\'' && i + 1 < input.length()) {
                    // 1) `'(?:[sdtm]|ll|ve|re)` - contractions, such as the suffixes of `he's`, `I'd`, `'tis`, `I'm`, `you'll`, `we've`, `they're`

                    var c1 = input.charAt(i);
                    if ("sdtm".indexOf(c1) >= 0) {
                        currentToken.append(c0).append(c1);
                        addMatch(tokens, currentToken);
                        i += 2;
                    } else if (i + 2 < input.length()) {
                        var c2 = input.charAt(i + 1);
                        if ((c1 == 'l' && c2 == 'l') || (c1 == 'v' && c2 == 'e') || (c1 == 'r' && c2 == 'e')) {
                            currentToken.append(c1).append(c2);
                            addMatch(tokens, currentToken);
                            i += 3;
                        }
                    }
                }

                if (Character.isLetter(c0) ||
                        (i + 1 < input.length() && !Character.isLetter(c0) && !Character.isDigit(c0) && c0 != '\r' && c0 != '\n' && Character.isLetter(input.charAt(i + 1)))) {
                    // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`
                    currentToken.append(c0);
                    i++;

                    while (i < input.length()) {
                        c0 = input.charAt(i);
                        if (!Character.isLetter(c0)) {
                            break;
                        }
                        currentToken.append(c0);
                        i++;
                    }

                    addMatch(tokens, currentToken);
                } else if (Character.isDigit(c0)) {
                    // 3) `\p{N}{1,3}` - numbers, such as `4`, `235`
                    currentToken.append(c0);
                    i++;
                    var c1 = input.charAt(i);
                    if (Character.isDigit(c1)) {
                        currentToken.append(c1);
                        i++;
                        var c2 = input.charAt(i);
                        if (Character.isDigit(c2)) {
                            currentToken.append(c2);
                            i++;
                        }
                    }
                    addMatch(tokens, currentToken);
                } else if ((!Character.isWhitespace(c0) && !Character.isLetter(c0) && !Character.isDigit(c0)) ||
                        (i + 1 < input.length() && !Character.isWhitespace(input.charAt(i + 1)) && !Character.isLetter(input.charAt(i + 1)) && !Character.isDigit(input.charAt(i + 1)))) {
                    // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, `.`, `"`
                    currentToken.append(c0);
                    i++;

                    while (i < input.length()) {
                        c0 = input.charAt(i);
                        if (Character.isWhitespace(c0) || Character.isLetter(c0) || Character.isDigit(c0)) {
                            break;
                        }
                        currentToken.append(c0);
                        i++;
                    }

                    while (i < input.length()) {
                        c0 = input.charAt(i);
                        if (c0 != '\r' && c0 != '\n') {
                            break;
                        }

                        currentToken.append(c0);
                        i++;
                    }

                    addMatch(tokens, currentToken);
                } else if (Character.isWhitespace(c0)) {
                    // 5) `\s*[\r\n]` - line endings such as `\r\n    \r\n`
                    // 6) `\s+(?!\S)` - whitespaces such as `               ` or ` `
                    // 7) `\s+` - unmatched remaining spaces, such as ` `
                    currentToken.append(c0);
                    i++;

                    while (i < input.length()) {
                        c0 = input.charAt(i);
                        if (!Character.isWhitespace(c0) || (i + 1 < input.length() && !Character.isWhitespace(input.charAt(i + 1)))) {
                            break;
                        }

                        currentToken.append(c0);
                        i++;
                    }
                    addMatch(tokens, currentToken);
                }

                if (!currentToken.isEmpty()) {
                    addMatch(tokens, currentToken);
                }
            }
            return tokens;
        }
    }
}