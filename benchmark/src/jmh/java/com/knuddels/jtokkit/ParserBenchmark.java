package com.knuddels.jtokkit;

import org.openjdk.jmh.infra.Blackhole;

//Benchmark                                                  (dataFolderPath)  Mode  Cnt  Score   Error  Units
//ParserBenchmark.benchmarkIsLetterOrNumeric                           N/A    ss   10  0.335 ± 0.037   s/op
//ParserBenchmark.benchmarkIsNewline                                   N/A    ss   10  0.047 ± 0.020   s/op
//ParserBenchmark.benchmarkIsNewlineOrLetterOrNumeric                  N/A    ss   10  0.342 ± 0.019   s/op
//ParserBenchmark.benchmarkIsNumeric                                   N/A    ss   10  0.351 ± 0.023   s/op
//ParserBenchmark.benchmarkIsUnicodeLetter                             N/A    ss   10  0.378 ± 0.044   s/op
//ParserBenchmark.benchmarkIsUnicodeWhitespace                         N/A    ss   10  0.373 ± 0.009   s/op
//ParserBenchmark.benchmarkIsWhitespaceOrLetterOrNumeric               N/A    ss   10  0.363 ± 0.021   s/op
public class ParserBenchmark {
    //    @Benchmark
    public void benchmarkIsUnicodeWhitespace(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
                bh.consume(Parser.isWhitespace(cp));
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
                bh.consume(Parser.isLetter(cp));
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
