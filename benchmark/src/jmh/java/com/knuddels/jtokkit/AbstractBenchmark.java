package com.knuddels.jtokkit;

import org.openjdk.jmh.annotations.Benchmark;

@SuppressWarnings("DuplicatedCode")
public abstract class AbstractBenchmark {
    //    @Benchmark
    public long benchmarkCl100kBaseTokenCountOriginal(BenchmarkingState state) {
        var result = 0;
        var encoding = state.cl100kBaseOriginal;
        for (var fileContent : state.fileContents) {
            result += encoding.countTokens(fileContent);
        }
        if (result != state.expectedFileContentsCl100kBaseTokenCount) {
            throw new RuntimeException(String.format("Wrong token count: %d != %d", result, state.expectedFileContentsCl100kBaseTokenCount));
        }
        return result;
    }

    @Benchmark
    public int benchmarkCl100kBaseTokenCount(BenchmarkingState state) {
        var result = 0;
        for (var fileContent : state.fileContents) {
            result += state.cl100kBase.countTokens(fileContent);
        }
        if (result != state.expectedFileContentsCl100kBaseTokenCount) {
            throw new RuntimeException(String.format("Wrong token count: %d != %d", result, state.expectedFileContentsCl100kBaseTokenCount));
        }
        return result;
    }

    //    @Benchmark
    public long benchmarkCl100kBaseCharacterCount(BenchmarkingState state) {
        var result = 0L;
        for (var fileContent : state.fileContents) {
            result += state.cl100kBase.countSplitChars(fileContent);
        }
        if (result != state.expectedFileContentsCl100kBaseCharCount) {
            throw new RuntimeException(String.format("Wrong token count: %d != %d", result, state.expectedFileContentsCl100kBaseTokenCount));
        }
        return result;
    }
}
