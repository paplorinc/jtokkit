package com.knuddels.jtokkit;

// Before:
//Benchmark                                                  (dataFolderPath)  Mode  Cnt  Score   Error  Units
//SingleThreadedBenchmark.benchmarkCl100kBaseCharacterCount              data    ss   10  1.683 ± 0.050   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCount                  data    ss   10  3.828 ± 0.190   s/op
//
// After:
//Benchmark                                                  (dataFolderPath)  Mode  Cnt  Score   Error  Units
//SingleThreadedBenchmark.benchmarkCl100kBaseCharacterCount              data    ss   10  0.614 ± 0.014   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCount                  data    ss   10  1.987 ± 0.057   s/op
public class SingleThreadedBenchmark extends AbstractBenchmark {

}
