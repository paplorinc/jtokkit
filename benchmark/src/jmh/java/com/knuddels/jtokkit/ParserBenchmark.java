package com.knuddels.jtokkit;

import org.openjdk.jmh.infra.Blackhole;

//Benchmark                                               (dataFolderPath)  Mode  Cnt  Score    Error  Units
//ParserBenchmark.benchmarkCodePointAt                                data    ss   10  0.089 ±  0.002   s/op
//ParserBenchmark.benchmarkCodePoints                                 data    ss   10  0.063 ±  0.001   s/op
//ParserBenchmark.benchmarkIsLetterOrNumeric                          data    ss   10  0.219 ±  0.001   s/op
//ParserBenchmark.benchmarkIsNewline                                  data    ss   10  0.057 ±  0.001   s/op
//ParserBenchmark.benchmarkIsNewlineOrLetterOrNumeric                 data    ss   10  0.231 ±  0.001   s/op
//ParserBenchmark.benchmarkIsNumeric                                  data    ss   10  0.093 ±  0.001   s/op
//ParserBenchmark.benchmarkIsUnicodeLetter                            data    ss   10  0.218 ±  0.001   s/op
//ParserBenchmark.benchmarkIsUnicodeWhitespace                        data    ss   10  0.097 ±  0.001   s/op
//ParserBenchmark.benchmarkIsWhitespaceOrLetterOrNumeric              data    ss   10  0.245 ±  0.002   s/op
public class ParserBenchmark {
    //    @Benchmark
    public void benchmarkCodePointAt(BenchmarkingState state) {
        long sum = 0;
        for (var fileContent : state.fileContents) {
            for (int i = 0; i < fileContent.length(); ) {
                var cp = fileContent.codePointAt(i);
                i += Character.charCount(cp);
                sum += cp;
            }
        }
        if (sum != 14_733_534_347L) {
            throw new IllegalStateException("" + sum);
        }
    }

//    @Benchmark

    public void benchmarkCodePoints(BenchmarkingState state) {
        long[] sum = {0};
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(i -> sum[0] += i);
        }
        if (sum[0] != 14_733_534_347L) {
            throw new IllegalStateException("" + sum[0]);
        }
    }

    //    @Benchmark
    public void benchmarkIsUnicodeWhitespace(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Parser.isWhitespace(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Parser.isNumeric(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsUnicodeLetter(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Parser.isLetter(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Parser.isLetterOrNumeric(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsNewline(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Parser.isNewline(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsWhitespaceOrLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Parser.isWhitespaceOrLetterOrNumeric(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsNewlineOrLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Parser.isNewlineOrLetterOrNumeric(cp)));
        }
    }
}
