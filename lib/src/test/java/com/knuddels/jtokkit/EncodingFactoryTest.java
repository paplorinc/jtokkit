package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.knuddels.jtokkit.reference.Cl100kBaseTestTest.TEXTS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.rangeClosed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EncodingFactoryTest {
    @Test
    void avoidCreatingUnnecessaryStrings() {
        var regexParts = List.of(
                "",
                "'(?i:s|t|re|ve|m|ll|d)",
                "[^\\r\\n\\p{L}\\p{N}]?\\p{L}+",
                "\\p{N}{1,3}",
                " ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*",
                "\\s*[\\r\\n]+",
                "\\s+(?!\\S)",
                "\\s+"
        );
        var finalRegex = regexParts.stream().skip(1).map(x -> "(" + x + ")").collect(joining("|"));
        assert finalRegex.equals("('(?i:s|t|re|ve|m|ll|d))|([^\\r\\n\\p{L}\\p{N}]?\\p{L}+)|(\\p{N}{1,3})|( ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*)|(\\s*[\\r\\n]+)|(\\s+(?!\\S))|(\\s+)");
        var pattern = Pattern.compile(finalRegex, Pattern.UNICODE_CHARACTER_CLASS);

        var encounters = regexParts.stream().map(x -> new HashSet<>()).collect(toList());
        for (var text : TEXTS) {
            for (var matcher = pattern.matcher(text); matcher.find(); ) {
                var match = matcher.group(0);
                @SuppressWarnings("OptionalGetWithoutIsPresent")
                var index = rangeClosed(1, matcher.groupCount())
                        .filter(i -> matcher.group(i) != null).findFirst()
                        .getAsInt();
                assert Objects.equals(match, matcher.group(index)) : "Mismatch between match and group " + index + " for text: " + text;
                encounters.get(index).add(matcher.group());
            }
        }
        assert encounters.stream().filter(x -> !x.isEmpty()).count() == regexParts.size() - 1;
    }

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