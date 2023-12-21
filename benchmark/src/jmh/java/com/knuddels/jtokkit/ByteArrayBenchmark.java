package com.knuddels.jtokkit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

@State(Scope.Benchmark)
@OutputTimeUnit(MICROSECONDS)
public class ByteArrayBenchmark {
    private List<ByteArray> byteArrays;

    @Setup
    public void setup() {
        var random = new Random();
        byteArrays = new ArrayList<>();
        for (var size = 8; size <= 100; size++) {
            var bytes = new byte[size];
            random.nextBytes(bytes);

            for (var i = 8; i <= size; i++) {
                byteArrays.add(new ByteArray(bytes, 0, i));
            }
            for (var i = 8; i + Long.BYTES < size; i++) {
                byteArrays.add(new ByteArray(bytes, i, bytes.length));
            }
        }
    }

    @Benchmark
    public void benchmarkBaseline(Blackhole bh) {
        for (var byteArray : byteArrays) {
            bh.consume(byteArray);
        }
    }

    @Benchmark
    public void benchmarkHashCode(Blackhole bh) {
        for (var byteArray : byteArrays) {
            bh.consume(byteArray.hashCode());
        }
    }
}
