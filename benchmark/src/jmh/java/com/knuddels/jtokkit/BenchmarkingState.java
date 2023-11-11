package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.util.List;

@State(Scope.Benchmark)
public class BenchmarkingState {
    public final Encoding cl100kBase = EncodingFactory.cl100kBase();
    public final Encoding p50kBase = EncodingFactory.p50kBase();
    public final Encoding p50kEdit = EncodingFactory.p50kEdit();
    public final Encoding r50kBase = EncodingFactory.r50kBase();
    public List<String> fileContents;
    public int expectedFileContentsCl100kBaseTokenCount;
    public int expectedFileContentsCl100kBaseCharCount;
    @Param("data")
    public String dataFolderPath;

    @Setup()
    public void setup() throws IOException {
        fileContents = BenchmarkUtils.loadData(dataFolderPath);
        expectedFileContentsCl100kBaseTokenCount = fileContents.stream()
                .mapToInt(x -> cl100kBase.encode(x).size())
                .sum();
        expectedFileContentsCl100kBaseCharCount = fileContents.stream()
                .mapToInt(String::length)
                .sum();
    }
}
