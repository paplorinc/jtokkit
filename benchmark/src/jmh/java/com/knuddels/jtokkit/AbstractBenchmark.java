package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import org.openjdk.jmh.annotations.Benchmark;

import java.util.List;

import static java.util.stream.Collectors.toList;

public abstract class AbstractBenchmark {

    @Benchmark
    public int benchmarkCl100kBaseTokenCount(BenchmarkingState state) {
        var result = countTokens(state.cl100kBase, state.fileContents);
        if (result != state.expectedFileContentsCl100kBaseTokenCount) {
            throw new RuntimeException(String.format("Wrong token count: %d != %d", result, state.expectedFileContentsCl100kBaseTokenCount));
        }
        return result;
    }

    protected int countTokens(Encoding encoding, List<String> fileContents) {
        return fileContents.stream()
                .mapToInt(encoding::countTokens)
                .sum();
    }

    protected List<List<Integer>> encodeAll(final Encoding encoding, final List<String> fileContents) {
        return fileContents.stream()
                .map(encoding::encode)
                .collect(toList());
    }
}
