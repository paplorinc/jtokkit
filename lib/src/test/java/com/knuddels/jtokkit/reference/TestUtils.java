package com.knuddels.jtokkit.reference;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TestUtils {

    public static IntList parseEncodingString(final String encodingString) {
        var results = Arrays.stream(
                        encodingString.substring(1, encodingString.length() - 1)
                                .replaceAll(" ", "")
                                .split(",")
                ).map(Integer::parseInt)
                .collect(Collectors.toList());
        return new IntArrayList(results);
    }
}
