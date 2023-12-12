package com.knuddels.jtokkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

class Profiling {
    public static void main2(String[] args) throws IOException {
        var encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
        var bigFileContent = Files.readString(Path.of("/Users/lorinc/IdeaProjects/jtokkit/benchmark/data/test_8_20000.txt"), UTF_8);
        var sum = 0;
        for (int i = 0; i < 5; i++) {
            sum += encoding.countTokens(bigFileContent);
        }
        System.out.println(sum);
    }
}