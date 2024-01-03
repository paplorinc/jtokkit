package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

@State(Scope.Benchmark)
public class BenchmarkingState {
    public final GptBytePairEncodingOriginal cl100kBaseOriginal = GptBytePairEncodingOriginal.getEncoder();
    public final Encoding cl100kBase = EncodingFactory.cl100kBase();
    public final Encoding p50kBase = EncodingFactory.p50kBase();
    public final Encoding p50kEdit = EncodingFactory.p50kEdit();
    public final Encoding r50kBase = EncodingFactory.r50kBase();
    public List<String> fileContents;
    public List<String> bigFileContents;
    @Param("data")
    public String dataFolderPath;

    @Setup
    public void setup() {
        fileContents = BenchmarkUtils.loadData(dataFolderPath);
        bigFileContents = Stream.of(
                        "test_0_20000.txt",
                        "test_1_20000.txt",
                        "test_2_20000.txt",
                        "test_3_20000.txt",
                        "test_4_20000.txt",
                        "test_5_20000.txt",
                        "test_6_20000.txt",
                        "test_7_20000.txt",
                        "test_8_20000.txt",
                        "test_9_20000.txt",
                        "test_10_20000.txt",
                        "test_11_20000.txt",
                        "test_12_20000.txt",
                        "test_13_20000.txt",
                        "test_14_20000.txt",
                        "test_15_20000.txt"
                ).map(x -> {
                    try {
                        return Files.readString(Path.of("/Users/lorinc/IdeaProjects/jtokkit/benchmark/data/" + x), UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }
}
