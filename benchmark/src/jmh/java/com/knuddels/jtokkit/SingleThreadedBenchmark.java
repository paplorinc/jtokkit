package com.knuddels.jtokkit;

// Before:
//Benchmark                                                  (dataFolderPath)  Mode  Cnt  Score   Error  Units
//SingleThreadedBenchmark.benchmarkCl100kBaseCharacterCount              data    ss   10  1.683 ± 0.050   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCount                  data    ss   10  3.828 ± 0.190   s/op
//
// After:
//Benchmark                                                  (dataFolderPath)  Mode  Cnt  Score   Error  Units
//SingleThreadedBenchmark.benchmarkCl100kBaseCharacterCount              data    ss   10  1.156 ± 0.027   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCount                  data    ss   10  2.466 ± 0.046   s/op
public class SingleThreadedBenchmark extends AbstractBenchmark {

}
