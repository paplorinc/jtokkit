package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.openjdk.jmh.infra.Blackhole;

//Benchmark                                                                    (dataFolderPath)  Mode  Cnt  Score    Error  Units
//Cl100kParserBenchmark.benchmarkIsLetter                                                  data    ss   10  0.245 ±  0.001   s/op
//Cl100kParserBenchmark.benchmarkIsLetterOrNumeric                                         data    ss   10  0.247 ±  0.001   s/op
//Cl100kParserBenchmark.benchmarkIsNewline                                                 data    ss   10  0.092 ±  0.001   s/op
//Cl100kParserBenchmark.benchmarkIsNotNewlineOrLetterOrNumeric                             data    ss   10  0.365 ±  0.001   s/op
//Cl100kParserBenchmark.benchmarkIsNotWhitespaceOrLetterOrNumeric                          data    ss   10  0.450 ±  0.004   s/op
//Cl100kParserBenchmark.benchmarkIsNumeric                                                 data    ss   10  0.122 ±  0.001   s/op
//Cl100kParserBenchmark.benchmarkIsWhitespace                                              data    ss   10  0.175 ±  0.066   s/op
//Cl100kParserBenchmark.benchmarkToUtf8Conversion                                          data    ss   10  0.211 ±  0.001   s/op
public class Cl100kParserBenchmark {
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
    public void benchmarkIsNotNewlineOrLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNotNewlineOrLetterOrNumeric(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsNotWhitespaceOrLetterOrNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNotWhitespaceOrLetterOrNumeric(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsNumeric(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isNumeric(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkIsWhitespace(BenchmarkingState state, Blackhole bh) {
        for (var fileContent : state.fileContents) {
            fileContent.codePoints().forEachOrdered(cp -> bh.consume(Cl100kParser.isWhitespace(cp)));
        }
    }

    //    @Benchmark
    public void benchmarkToUtf8Conversion(BenchmarkingState state, Blackhole bh) {
        var dst = new ByteArrayList();
        for (var fileContent : state.fileContents) {
            bh.consume(Cl100kParser.addUtf8Bytes(fileContent, 0, fileContent.length(), dst));
        }
    }
}
