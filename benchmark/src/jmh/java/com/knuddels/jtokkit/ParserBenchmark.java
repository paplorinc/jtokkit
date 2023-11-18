package com.knuddels.jtokkit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

//Benchmark                                                  (dataFolderPath)  Mode  Cnt  Score   Error  Units
//ParserBenchmark.benchmarkIsLetterOrNumeric                              N/A    ss   10  0.346 ± 0.054   s/op
//ParserBenchmark.benchmarkIsNewline                                      N/A    ss   10  0.056 ± 0.011   s/op
//ParserBenchmark.benchmarkIsNumeric                                      N/A    ss   10  0.329 ± 0.028   s/op
//ParserBenchmark.benchmarkIsUnicodeLetter                                N/A    ss   10  0.363 ± 0.031   s/op
//ParserBenchmark.benchmarkIsUnicodeWhitespace                            N/A    ss   10  0.429 ± 0.125   s/op
//ParserBenchmark.benchmarkIsWhitespaceLetterOrNumeric                    N/A    ss   10  0.693 ± 0.018   s/op
public class ParserBenchmark {
    @Benchmark
    public void benchmarkIsUnicodeWhitespace(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isUnicodeWhitespace(cp));
            }
        }
    }

    @Benchmark
    public void benchmarkIsNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isNumeric(cp));
            }
        }
    }

    @Benchmark
    public void benchmarkIsUnicodeLetter(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isUnicodeLetter(cp));
            }
        }
    }

    @Benchmark
    public void benchmarkIsLetterOrNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isLetterOrNumeric(cp));
            }
        }
    }

    @Benchmark
    public void benchmarkIsNewline(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(cp == '\n' || cp == '\r');
            }
        }
    }

    @Benchmark
    public void benchmarkIsWhitespaceLetterOrNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isWhitespaceOrLetterOrNumeric(cp));
            }
        }
    }
}
