package com.knuddels.jtokkit.reference;

import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;

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
        return IntLists.immutable.ofAll(results);
    }
}
