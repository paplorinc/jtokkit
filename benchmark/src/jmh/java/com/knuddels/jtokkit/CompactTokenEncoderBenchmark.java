package com.knuddels.jtokkit;

import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

//Benchmark                                                          Mode  Cnt      Score     Error  Units
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_0      ss   10    326.283 ± 757.502  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_1      ss   10   2362.108 ± 315.128  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_2      ss   10   2432.817 ± 233.735  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_3      ss   10   2493.408 ± 246.531  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_4      ss   10   2561.212 ± 268.099  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_5      ss   10   2604.675 ± 294.316  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_6      ss   10   2633.225 ± 229.965  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_7      ss   10   2795.637 ± 230.083  us/op
//CompactTokenEncoderBenchmark.benchmarkCompactTokenEncoderFrom_all    ss   10  15528.771 ± 886.308  us/op

@State(Scope.Benchmark)
@OutputTimeUnit(MICROSECONDS)
public class CompactTokenEncoderBenchmark {
    private final byte[] BYTE_ARRAY = new byte[1_000_000];
    private final int MAX_BYTE_ARRAY_LENGTH = BYTE_ARRAY.length - Byte.SIZE;

    @Setup()
    public void setup() throws IOException {
        for (int i = 0; i < BYTE_ARRAY.length; i++) {
            BYTE_ARRAY[i] = (byte) (i % 256);
        }
    }

    //    @Benchmark
    public void benchmarkCompactTokenEncoderFrom_0(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(i);
        }
    }

    //    @Benchmark
    public void benchmarkCompactTokenEncoderFrom_1(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 1));
        }
    }

    //    @Benchmark
    public void benchmarkCompactTokenEncoderFrom_2(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 2));
        }
    }

    //    @Benchmark
    public void benchmarkCompactTokenEncoderFrom_3(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 3));
        }
    }

    //    @Benchmark
    public void benchmarkCompactTokenEncoderFrom_4(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 4));
        }
    }

    //    @Benchmark
    public void benchmarkCompactTokenEncoderFrom_5(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 5));
        }
    }

    //    @Benchmark
    public void benchmarkCompactTokenEncoderFrom_6(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 6));
        }
    }

    //    @Benchmark
    public void benchmarkCompactTokenEncoderFrom_7(Blackhole bh) {
        for (int i = 0; i < MAX_BYTE_ARRAY_LENGTH; i++) {
            bh.consume(CompactTokenEncoder.from(BYTE_ARRAY, i, i + 7));
        }
    }

    //    @Benchmark
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
