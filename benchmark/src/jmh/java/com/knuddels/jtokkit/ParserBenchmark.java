package com.knuddels.jtokkit;

import org.openjdk.jmh.infra.Blackhole;

//Benchmark                                                  (dataFolderPath)  Mode  Cnt  Score   Error  Units
//ParserBenchmark.benchmarkIsLetterOrNumeric                              N/A    ss   10  0.321 ± 0.044   s/op
//ParserBenchmark.benchmarkIsNewline                                      N/A    ss   10  0.046 ± 0.020   s/op
//ParserBenchmark.benchmarkIsNewlineOrLetterOrNumeric                     N/A    ss   10  0.331 ± 0.029   s/op
//ParserBenchmark.benchmarkIsNumeric                                      N/A    ss   10  0.320 ± 0.022   s/op
//ParserBenchmark.benchmarkIsUnicodeLetter                                N/A    ss   10  0.339 ± 0.028   s/op
//ParserBenchmark.benchmarkIsUnicodeWhitespace                            N/A    ss   10  0.358 ± 0.008   s/op
//ParserBenchmark.benchmarkIsWhitespaceOrLetterOrNumeric                  N/A    ss   10  0.352 ± 0.018   s/op
public class ParserBenchmark {
    //    @Benchmark
    public void benchmarkIsUnicodeWhitespace(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isUnicodeWhitespace(cp));
            }
        }
    }

    //    @Benchmark
    public void benchmarkIsNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isNumeric(cp));
            }
        }
    }

    //    @Benchmark
    public void benchmarkIsUnicodeLetter(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isUnicodeLetter(cp));
            }
        }
    }

    //    @Benchmark
    public void benchmarkIsLetterOrNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isLetterOrNumeric(cp));
            }
        }
    }

    //    @Benchmark
    public void benchmarkIsNewline(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isNewline(cp));
            }
        }
    }

    //    @Benchmark
    public void benchmarkIsWhitespaceOrLetterOrNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isWhitespaceOrLetterOrNumeric(cp));
            }
        }
    }

    //    @Benchmark
    public void benchmarkIsNewlineOrLetterOrNumeric(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isNewlineOrLetterOrNumeric(cp));
            }
        }
    }
}
