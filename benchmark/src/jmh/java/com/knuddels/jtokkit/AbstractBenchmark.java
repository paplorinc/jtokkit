package com.knuddels.jtokkit;

import org.openjdk.jmh.annotations.Benchmark;

public abstract class AbstractBenchmark {

    //    @Benchmark
    public int benchmarkCl100kBaseTokenCount(BenchmarkingState state) {
        var result = 0;
        var encoding = state.cl100kBase;
        for (var fileContent : state.fileContents) {
            var i = encoding.countTokens(fileContent);
            result += i;
        }
        if (result != state.expectedFileContentsCl100kBaseTokenCount) {
            throw new RuntimeException(String.format("Wrong token count: %d != %d", result, state.expectedFileContentsCl100kBaseTokenCount));
        }
        return result;
    }

    @Benchmark
    public long benchmarkCl100kBaseCharacterCount(BenchmarkingState state) {
        var result = 0L;
        var encoding = state.cl100kBase;
        for (var fileContent : state.fileContents) {
            result += encoding.countSplitChars(fileContent);
        }
        if (result != state.expectedFileContentsCl100kBaseCharCount) {
            throw new RuntimeException(String.format("Wrong token count: %d != %d", result, state.expectedFileContentsCl100kBaseTokenCount));
        }
        return result;
    }

}
