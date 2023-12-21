package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;

import java.util.List;
import java.util.stream.Collectors;

//Benchmark                                                                    (dataFolderPath)  Mode  Cnt      Score      Error  Units
//SingleThreadedBenchmark.benchmarkCl100kBaseCountBytes                                    data    ss   10      0.597 ±    0.005   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCount                                    data    ss   10      1.655 ±    0.021   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCountBigFileContent                      data    ss   10      0.028 ±    0.003   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCountBigFileContentOriginal              data    ss   10      0.674 ±    0.002   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCountOriginal                            data    ss   10      9.785 ±    0.134   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokens                                        data    ss   10      1.746 ±    0.049   s/op
public class SingleThreadedBenchmark extends AbstractBenchmark {

    @Override
    protected List<List<Integer>> encodeAll(final Encoding encoding, final List<String> fileContents) {
        return fileContents.stream()
                .map(encoding::encode)
                .collect(Collectors.toList());
    }
}
