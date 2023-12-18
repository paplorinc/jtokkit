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

import static java.nio.charset.StandardCharsets.UTF_8;

@State(Scope.Benchmark)
public class BenchmarkingState {
    public final GptBytePairEncodingOriginal cl100kBaseOriginal = GptBytePairEncodingOriginal.getEncoder();
    public final Encoding cl100kBase = EncodingFactory.cl100kBase();
    //    public final Encoding p50kBase = EncodingFactory.p50kBase();
//    public final Encoding p50kEdit = EncodingFactory.p50kEdit();
//    public final Encoding r50kBase = EncodingFactory.r50kBase();
    public List<String> fileContents;
    public String bigFileContent;
    public int expectedFileContentsCl100kBaseTokenCount;
    public int expectedFileContentsCl100kBaseByteCount;
    @Param("data")
    public String dataFolderPath;

    @Setup()
    public void setup() throws IOException {
        fileContents = BenchmarkUtils.loadData(dataFolderPath);
        bigFileContent = Files.readString(Path.of("/Users/lorinc/IdeaProjects/jtokkit/benchmark/data/test_8_20000.txt"), UTF_8);
        expectedFileContentsCl100kBaseTokenCount = fileContents.stream()
                .mapToInt(x -> cl100kBase.encode(x).size())
                .sum();
        expectedFileContentsCl100kBaseByteCount = fileContents.stream()
                .mapToInt(s -> s.getBytes(UTF_8).length)
                .sum();
    }
}
