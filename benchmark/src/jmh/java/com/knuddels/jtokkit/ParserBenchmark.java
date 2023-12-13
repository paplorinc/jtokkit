package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

//Benchmark                                                                     (dataFolderPath)  Mode  Cnt  Score    Error  Units
//ParserBenchmark.benchmarkIsLetter                                                        data    ss   10  0.239 ± 0.003   s/op
//ParserBenchmark.benchmarkIsLetterOrNumeric                                               data    ss   10  0.256 ± 0.011   s/op
//ParserBenchmark.benchmarkIsNewline                                                       data    ss   10  0.078 ± 0.001   s/op
//ParserBenchmark.benchmarkIsNewlineOrLetterOrNumeric                                      data    ss   10  0.243 ± 0.003   s/op
//ParserBenchmark.benchmarkIsNumeric                                                       data    ss   10  0.121 ± 0.001   s/op
//ParserBenchmark.benchmarkIsWhitespace                                                    data    ss   10  0.115 ± 0.003   s/op
//ParserBenchmark.benchmarkIsWhitespaceOrLetterOrNumeric                                   data    ss   10  0.269 ± 0.010   s/op
//ParserBenchmark.benchmarkToUtf8Conversion                                                data    ss   10  0.158 ± 0.001   s/op
public class ParserBenchmark {
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
    public void benchmarkIsWhitespaceOrLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isWhitespaceOrLetterOrNumeric(cp)));
        }
    }

    @Benchmark
    public void benchmarkIsNewlineOrLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNewlineOrLetterOrNumeric(cp)));
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
