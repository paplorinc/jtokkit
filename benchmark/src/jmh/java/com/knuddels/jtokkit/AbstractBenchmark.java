package com.knuddels.jtokkit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

@SuppressWarnings("DuplicatedCode")
public abstract class AbstractBenchmark {
    @Benchmark
    public long benchmarkCl100kBaseCountBytes(BenchmarkingState state) {
        var result = 0L;
        var encoding = state.cl100kBase;
        for (var fileContent : state.fileContents) {
            result += encoding.countBytes(fileContent);
        }
        if (result != state.expectedFileContentsCl100kBaseByteCount) {
            throw new RuntimeException(String.format("Wrong token count: %d != %d", result, state.expectedFileContentsCl100kBaseTokenCount));
        }
        return result;
    }

    @Benchmark
    public int benchmarkCl100kBaseTokenCount(BenchmarkingState state) {
        var result = 0;
        var encoding = state.cl100kBase;
        for (var fileContent : state.fileContents) {
            result += encoding.countTokens(fileContent);
        }
        if (result != state.expectedFileContentsCl100kBaseTokenCount) {
            throw new RuntimeException(String.format("Wrong token count: %d != %d", result, state.expectedFileContentsCl100kBaseTokenCount));
        }
        return result;
    }

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

    //    @Benchmark
    public int benchmarkCl100kBaseTokens(BenchmarkingState state) {
        var result = 0;
        var encoding = state.cl100kBase;
        for (var fileContent : state.fileContents) {
            result += encoding.encode(fileContent).size();
        }
        if (result != state.expectedFileContentsCl100kBaseTokenCount) {
            throw new RuntimeException(String.format("Wrong token count: %d != %d", result, state.expectedFileContentsCl100kBaseTokenCount));
        }
        return result;
    }

    //    @Benchmark
    public void benchmarkCl100kBaseTokenCountBigFileContent(BenchmarkingState state, Blackhole bh) {
        for (int i = 0; i < 5; i++) {
            bh.consume(state.cl100kBase.countTokens(state.bigFileContent));
        }
    }

    //    @Benchmark
    public void benchmarkCl100kBaseTokenCountBigFileContentOriginal(BenchmarkingState state, Blackhole bh) {
        for (int i = 0; i < 5; i++) {
            bh.consume(state.cl100kBaseOriginal.countTokens(state.bigFileContent));
        }
    }
}
