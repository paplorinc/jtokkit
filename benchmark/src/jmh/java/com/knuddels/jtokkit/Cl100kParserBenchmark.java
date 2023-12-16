package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

//Benchmark                                                                    (dataFolderPath)  Mode  Cnt  Score   Error  Units
//Cl100kParserBenchmark.benchmarkIsLetter                                                  data    ss   10  0.239 ± 0.001   s/op
//Cl100kParserBenchmark.benchmarkIsLetterOrNumeric                                         data    ss   10  0.248 ± 0.006   s/op
//Cl100kParserBenchmark.benchmarkIsNewline                                                 data    ss   10  0.079 ± 0.001   s/op
//Cl100kParserBenchmark.benchmarkIsNotNewlineOrLetterOrNumeric                             data    ss   10  0.372 ± 0.005   s/op
//Cl100kParserBenchmark.benchmarkIsNotWhitespaceOrLetterOrNumeric                          data    ss   10  0.427 ± 0.013   s/op
//Cl100kParserBenchmark.benchmarkIsNumeric                                                 data    ss   10  0.116 ± 0.001   s/op
//Cl100kParserBenchmark.benchmarkIsWhitespace                                              data    ss   10  0.211 ± 0.037   s/op
//Cl100kParserBenchmark.benchmarkToUtf8Conversion                                          data    ss   10  0.179 ± 0.031   s/op
public class Cl100kParserBenchmark {
    @Benchmark
    public void benchmarkIsWhitespace(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isWhitespace(cp)));
        }
    }

    @Benchmark
    public void benchmarkIsNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNumeric(cp)));
        }
    }

    @Benchmark
    public void benchmarkIsLetter(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isLetter(cp)));
        }
    }

    @Benchmark
    public void benchmarkIsLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isLetterOrNumeric(cp)));
        }
    }

    @Benchmark
    public void benchmarkIsNewline(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNewline(cp)));
        }
    }

    @Benchmark
    public void benchmarkIsNotWhitespaceOrLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNotWhitespaceOrLetterOrNumeric(cp)));
        }
    }

    @Benchmark
    public void benchmarkIsNotNewlineOrLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNotNewlineOrLetterOrNumeric(cp)));
        }
    }

    @Benchmark
    public void benchmarkToUtf8Conversion(BenchmarkingState state, Blackhole bh) {
        var dst = new ByteArrayList();
        for (var fileContent : state.fileContents) {
            bh.consume(Cl100kParser.toUtf8Bytes(fileContent, 0, fileContent.length(), dst));
        }
    }
}
