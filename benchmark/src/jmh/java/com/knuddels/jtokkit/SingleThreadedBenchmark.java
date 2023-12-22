package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.stream.Collectors;

//Benchmark                                                                    (dataFolderPath)  Mode  Cnt      Score      Error  Units
//SingleThreadedBenchmark.benchmarkCl100kBaseCountBytes                                    data    ss   10      0.597 ±    0.005   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCount                                    data    ss   10      1.663 ±    0.021   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCountBigFileContent                      data    ss   10      0.028 ±    0.003   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCountBigFileContentOriginal              data    ss   10      0.674 ±    0.002   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCountOriginal                            data    ss   10      9.785 ±    0.134   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokens                                        data    ss   10      1.734 ±    0.049   s/op
public class SingleThreadedBenchmark extends AbstractBenchmark {

    @Benchmark
    public int benchmarkCl100kBaseTokenCount(BenchmarkingState state) {
        var result = 0;
        var encoding = state.cl100kBase;
        for (var fileContent : state.fileContents) {
            result += encoding.countTokens(fileContent);
        }
        return result;
    }

    @Benchmark
    public void benchmarkCl100kBaseTokenCountBigFileContent(BenchmarkingState state, Blackhole bh) {
        for (int i = 0; i < 5; i++) {
            bh.consume(state.cl100kBase.countTokens(state.bigFileContent));
        }
    }

    @Benchmark
    public void benchmarkCl100kBaseTokenCountBigFileContentOriginal(BenchmarkingState state, Blackhole bh) {
        for (int i = 0; i < 5; i++) {
            bh.consume(state.cl100kBaseOriginal.countTokens(state.bigFileContent));
        }
    }

    @Override
    protected List<List<Integer>> encodeAll(final Encoding encoding, final List<String> fileContents) {
        return fileContents.stream()
                .map(encoding::encode)
                .collect(Collectors.toList());
    }
}
