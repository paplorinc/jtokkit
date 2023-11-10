package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import java.util.List;
import java.util.stream.Collectors;

// Before:
//Benchmark                                              (dataFolderPath)  Mode  Cnt  Score   Error  Units
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCount              data    ss   10  3.828 ± 0.190   s/op
//
// After:
//Benchmark                                              (dataFolderPath)  Mode  Cnt  Score   Error  Units
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCount              data    ss   10  2.864 ± 0.174   s/op
public class SingleThreadedBenchmark extends AbstractBenchmark {

}
