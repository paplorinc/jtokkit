package com.knuddels.jtokkit;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;

public class FileDownloader {

    public static void main(String[] args) {
        int[] top100BookIds = {
                10,
                11,
                16,
                23,
                35,
                36,
                41,
                43,
                45,
                46,
                55,
                74,
                76,
                84,
                98,
                100,
                120,
                132,
                140,
                158,
                161,
                174,
                203,
                205,
                209,
                215,
                219,
                236,
                244,
                345,
                408,
                512,
                514,
                521,
                600,
                730,
                768,
                779,
                829,
                844,
                932,
                996,
                1080,
                1184,
                1232,
                1250,
                1251,
                1260,
                1342,
                1399,
                1400,
                1497,
                1513,
                1661,
                1727,
                1952,
                1998,
                2000,
                2148,
                2542,
                2554,
                2591,
                2600,
                2680,
                2701,
                2814,
                2852,
                3207,
                3296,
                3825,
                4217,
                4300,
                4363,
                5200,
                5827,
                6130,
                6133,
                6753,
                7370,
                8492,
                8800,
                10007,
                11030,
                15399,
                16328,
                20203,
                24869,
                25282,
                25344,
                26184,
                27827,
                28054,
                30254,
                31284,
                35899,
                41445,
                42324,
                58585,
                64317,
                67098
        };
        SortedSet<Integer> uniqueBookIds = Arrays.stream(top100BookIds).boxed().collect(toCollection(TreeSet::new));
        assert uniqueBookIds.size() == 100; // top 100 books in txt format

        Path rootFolder = Paths.get("benchmark/data");
        uniqueBookIds.parallelStream()
                .forEach(bookId -> downloadBook(bookId, rootFolder));

        // Assert the total size (replace 0L with the expected total size)
        long totalSize = calculateTotalFileSize(rootFolder);
        if (totalSize != 73_174_598) {
            throw new AssertionError("Total size did not match expected value, actual: " + totalSize);
        }
    }


    public static void downloadBook(int bookId, Path rootFolder) {
        try {
            String fileName = String.format("%d.txt", bookId);
            Path localPath = rootFolder.resolve(fileName);
            URL fileUrl = new URL(String.format("https://www.gutenberg.org/cache/epub/%d/pg%d.txt", bookId, bookId));

            if (Files.exists(localPath)) {
                System.out.printf("File %s already exists, skipping download.%n", fileName);
            } else {
                System.out.printf("Downloading %s...\n", fileName);
                Files.copy(fileUrl.openStream(), localPath);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static long calculateTotalFileSize(Path rootFolder) {
        try (Stream<Path> paths = Files.walk(rootFolder)) {
            return paths.filter(Files::isRegularFile).mapToLong(FileDownloader::fileSize).sum();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long fileSize(Path path) {
        try {
            long size = Files.size(path);
            return size;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
