package com.knuddels.jtokkit;

import java.io.IOException;
import java.util.List;

class Profiling {
    private static final List<String> texts = Cl100kTest.getTexts("");

    public static void main2(String[] args) throws IOException {
        var encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
        var sum = 0L;
        for (String text : texts) {
            sum += encoding.countTokens(text);
        }
        System.out.println(sum);
    }
}