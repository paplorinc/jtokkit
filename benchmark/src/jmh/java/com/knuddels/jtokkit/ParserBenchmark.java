package com.knuddels.jtokkit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

//Benchmark                                                      (dataFolderPath)  Mode  Cnt  Score   Error  Units
//ParserBenchmark.benchmarkIsLetterOrNumeric                                  N/A    ss   10  0.124 ± 0.011   s/op
//ParserBenchmark.benchmarkIsNewline                                          N/A    ss   10  0.046 ± 0.020   s/op
//ParserBenchmark.benchmarkIsNewlineOrLetterOrNumeric                         N/A    ss   10  0.156 ± 0.011   s/op
//ParserBenchmark.benchmarkIsNumeric                                          N/A    ss   10  0.096 ± 0.012   s/op
//ParserBenchmark.benchmarkIsUnicodeLetter                                    N/A    ss   10  0.132 ± 0.002   s/op
//ParserBenchmark.benchmarkIsUnicodeWhitespace                                N/A    ss   10  0.095 ± 0.011   s/op
//ParserBenchmark.benchmarkIsWhitespaceOrLetterOrNumeric                      N/A    ss   10  0.198 ± 0.015   s/op
public class ParserBenchmark {
    @Benchmark
    public void benchmarkIsUnicodeWhitespace(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            bh.consume(Parser.isWhitespace(-1));
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isWhitespace(cp));
            }
        }
    }

    @Benchmark
    public void benchmarkIsNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            bh.consume(Parser.isNumeric(-1));
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isNumeric(cp));
            }
        }
    }

    @Benchmark
    public void benchmarkIsUnicodeLetter(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            bh.consume(Parser.isLetter(-1));
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isLetter(cp));
            }
        }
    }

    @Benchmark
    public void benchmarkIsLetterOrNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            bh.consume(Parser.isLetterOrNumeric(-1));
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isLetterOrNumeric(cp));
            }
        }
    }

    @Benchmark
    public void benchmarkIsNewline(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            bh.consume(Parser.isNewline(-1));
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isNewline(cp));
            }
        }
    }

    @Benchmark
    public void benchmarkIsWhitespaceOrLetterOrNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            bh.consume(Parser.isWhitespaceOrLetterOrNumeric(-1));
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isWhitespaceOrLetterOrNumeric(cp));
            }
        }
    }

    @Benchmark
    public void benchmarkIsNewlineOrLetterOrNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            bh.consume(Parser.isNewlineOrLetterOrNumeric(-1));
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isNewlineOrLetterOrNumeric(cp));
            }
        }
    }
}
