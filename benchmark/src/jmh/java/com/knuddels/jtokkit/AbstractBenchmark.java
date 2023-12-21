package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;

@SuppressWarnings("DuplicatedCode")
public abstract class AbstractBenchmark {
    //@Benchmark
    public void benchmarkP50kBase(final BenchmarkingState state, final Blackhole blackhole) {
        blackhole.consume(encodeAll(state.p50kBase, state.fileContents));
    }

    //@Benchmark
    public void benchmarkR50kBase(final BenchmarkingState state, final Blackhole blackhole) {
        blackhole.consume(encodeAll(state.r50kBase, state.fileContents));
    }

    //@Benchmark
    public void benchmarkP50kEdit(final BenchmarkingState state, final Blackhole blackhole) {
        blackhole.consume(encodeAll(state.p50kEdit, state.fileContents));
    }

    //@Benchmark
    public void benchmarkCl100kBase(final BenchmarkingState state, final Blackhole blackhole) {
        blackhole.consume(encodeAll(state.cl100kBase, state.fileContents));
    }

    /**
     * Encodes all file contents with the given encoding.
     *
     * @param encoding     the encoding to use
     * @param fileContents the file contents to encode
     * @return a list of encoded token lists
     */
    protected abstract List<List<Integer>> encodeAll(final Encoding encoding, final List<String> fileContents);

    //@Benchmark
    public long benchmarkCl100kBaseCountBytes(BenchmarkingState state) {
        var result = 0L;
        var encoding = state.cl100kBase;
        for (var fileContent : state.fileContents) {
            result += encoding.countBytes(fileContent);
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
        return result;
    }

    //@Benchmark
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

    //    @Benchmark
    public long benchmarkCl100kBaseTokenCountOriginal(BenchmarkingState state) {
        var result = 0;
        var encoding = state.cl100kBaseOriginal;
        for (var fileContent : state.fileContents) {
            result += encoding.countTokens(fileContent);
        }
        return result;
    }

    @Benchmark
    public int benchmarkCl100kBaseTokens(BenchmarkingState state) {
        var result = 0;
        var encoding = state.cl100kBase;
        for (var fileContent : state.fileContents) {
            result += encoding.encode(fileContent).size();
        }
        return result;
    }
}
