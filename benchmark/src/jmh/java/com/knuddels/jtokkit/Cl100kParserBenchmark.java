package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.openjdk.jmh.infra.Blackhole;

//Benchmark                                                                     (dataFolderPath)  Mode  Cnt  Score    Error  Units
//Cl100kParserBenchmark.benchmarkIsLetter                                                        data    ss   10  0.239 ±  0.003   s/op
//Cl100kParserBenchmark.benchmarkIsLetterOrNumeric                                               data    ss   10  0.273 ±  0.015   s/op
//Cl100kParserBenchmark.benchmarkIsNewline                                                       data    ss   10  0.078 ±  0.001   s/op
//Cl100kParserBenchmark.benchmarkIsNewlineOrLetterOrNumeric                                      data    ss   10  0.242 ±  0.001   s/op
//Cl100kParserBenchmark.benchmarkIsNumeric                                                       data    ss   10  0.121 ±  0.001   s/op
//Cl100kParserBenchmark.benchmarkIsWhitespace                                                    data    ss   10  0.114 ±  0.001   s/op
//Cl100kParserBenchmark.benchmarkIsWhitespaceOrLetterOrNumeric                                   data    ss   10  0.264 ±  0.005   s/op
//Cl100kParserBenchmark.benchmarkToUtf8Conversion                                                data    ss   10  0.158 ±  0.001   s/op
public class Cl100kParserBenchmark {
    //    @Benchmark
    public void benchmarkIsWhitespace(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isWhitespace(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNumeric(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsLetter(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isLetter(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isLetterOrNumeric(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsNewline(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNewline(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsWhitespaceOrLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isWhitespaceOrLetterOrNumeric(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsNewlineOrLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNewlineOrLetterOrNumeric(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkToUtf8Conversion(BenchmarkingState state, Blackhole bh) {
        var dst = new ByteArrayList();
        for (var fileContent : state.fileContents) {
            bh.consume(Cl100kParser.toUtf8Bytes(fileContent, 0, fileContent.length(), dst));
        }
    }
}
