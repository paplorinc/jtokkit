package com.knuddels.jtokkit;

import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

//Benchmark                                                                    (dataFolderPath)  Mode  Cnt      Score      Error  Units
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_0                           N/A    ss   10    316.946 ±  730.074  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_1                           N/A    ss   10    565.783 ± 1132.626  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_2                           N/A    ss   10    893.229 ± 1409.272  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_3                           N/A    ss   10   1258.379 ± 1232.418  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_4                           N/A    ss   10   2771.692 ± 1026.604  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_5                           N/A    ss   10   3186.883 ± 1040.693  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_6                           N/A    ss   10   3320.800 ±  970.823  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_7                           N/A    ss   10   3596.104 ±  948.423  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_all                         N/A    ss   10  11705.450 ± 3325.784  us/op
@State(Scope.Benchmark)
@OutputTimeUnit(MICROSECONDS)
public class CompactTokenEncoderBenchmark {
    private final byte[] BYTE_ARRAY = new byte[1_000_000];
    private final int MAX_BYTE_ARRAY_LENGTH = BYTE_ARRAY.length - Byte.SIZE;

    @Setup
    public void setup() throws IOException {
        for (int i = 0; i < BYTE_ARRAY.length; i++) {
            BYTE_ARRAY[i] = (byte) (i % 256);
        }
    }

    //@Benchmark
    public void benchmarkCompactTokenEncoderFrom_0(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(i);
        }
    }

    //@Benchmark
    public void benchmarkCompactTokenEncoderFrom_1(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 1));
        }
    }

    //@Benchmark
    public void benchmarkCompactTokenEncoderFrom_2(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 2));
        }
    }

    //@Benchmark
    public void benchmarkCompactTokenEncoderFrom_3(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 3));
        }
    }

    //@Benchmark
    public void benchmarkCompactTokenEncoderFrom_4(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 4));
        }
    }

    //@Benchmark
    public void benchmarkCompactTokenEncoderFrom_5(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 5));
        }
    }

    //@Benchmark
    public void benchmarkCompactTokenEncoderFrom_6(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 6));
        }
    }

    //@Benchmark
    public void benchmarkCompactTokenEncoderFrom_7(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 7));
        }
    }

    //@Benchmark
    public void benchmarkCompactTokenEncoderFrom_all(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 1));
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 2));
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 4));
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 5));
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 6));
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 7));
        }
    }
}
