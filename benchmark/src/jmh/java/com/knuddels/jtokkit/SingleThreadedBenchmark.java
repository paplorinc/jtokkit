package com.knuddels.jtokkit;

// Before:
//Benchmark                                                  (dataFolderPath)  Mode  Cnt  Score   Error  Units
//SingleThreadedBenchmark.benchmarkCl100kBaseCharacterCount              data    ss   10  1.683 ± 0.050   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCount                  data    ss   10  3.828 ± 0.190   s/op
//
// After:
//Benchmark                                                  (dataFolderPath)  Mode  Cnt  Score   Error  Units
//SingleThreadedBenchmark.benchmarkCl100kBaseCharacterCount              data    ss   10  0.583 ± 0.006   s/op
//SingleThreadedBenchmark.benchmarkCl100kBaseTokenCount                  data    ss   10  1.827 ± 0.065   s/op
public class SingleThreadedBenchmark extends AbstractBenchmark {

}
