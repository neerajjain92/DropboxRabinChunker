package com.byteproxy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RabinChunker {
    private static final int WINDOW_SIZE = 48;
    private static final int MIN_CHUNK = 2048;
    private static final int MAX_CHUNK = 8192;
    private static final int CHUNK_MASK = 0x1FFF;

    // Irreducible polynomial coefficients
    private static final long POLY = 0x3;
    private final long[] LOOKUP_TABLE = new long[256];

    private final byte[] window;
    private int windowPos;
    private long fingerprint;

    public RabinChunker() {
        this.window = new byte[WINDOW_SIZE];
        this.windowPos = 0;
        this.fingerprint = 0;
        initLookupTable();
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        RabinChunker chunker = new RabinChunker();

        // Read original file
        Path originalPath = Paths.get(Objects.requireNonNull(RabinChunker.class.getResource("/original.txt")).toURI());
        byte[] originalData = Files.readAllBytes(originalPath);

        // Create modified file by adding 'X' at the start
        Path modifiedPath = Paths.get(Objects.requireNonNull(RabinChunker.class.getResource("/modified.txt")).toURI());
        byte[] modifiedData = Files.readAllBytes(modifiedPath);

        List<byte[]> originalChunks = chunker.chunk(originalData);
        List<byte[]> modifiedChunks = chunker.chunk(modifiedData);

        // Print and compare chunks
        printChunks(originalChunks, "Original File");
        printChunks(modifiedChunks, "Modified File");

        // Compare chunks
        int differentChunks = 0;
        int minSize = Math.min(originalChunks.size(), modifiedChunks.size());

        for (int i = 0; i < minSize; i++) {
            if (!Arrays.equals(originalChunks.get(i), modifiedChunks.get(i))) {
                differentChunks++;
                System.out.println("\nChunk " + i + " differs:");
                System.out.println("Original Hash: " + String.format("0x%016X", calculateChunkHash(originalChunks.get(i))));
                System.out.println("Modified Hash: " + String.format("0x%016X", calculateChunkHash(modifiedChunks.get(i))));
            } else {
                System.out.println("\nResynchronization occurred at chunk: " + i);
                break;
            }
        }

        System.out.println("\nSummary:");
        System.out.println("Original chunks: " + originalChunks.size());
        System.out.println("Modified chunks: " + modifiedChunks.size());
        System.out.println("Number of different chunks: " + differentChunks);
        System.out.println("Percentage of chunks affected: " +
                String.format("%.2f%%", (differentChunks * 100.0) / originalChunks.size()));
    }

    private void initLookupTable() {
        // Initialize lookup table for polynomial calculations
        for (int i = 0; i < 256; i++) {
            long value = i;
            for (int j = 0; j < 8; j++) {
                if ((value & 1) == 1) {
                    value = (value >>> 1) ^ POLY;
                } else {
                    value = value >>> 1;
                }
            }
            LOOKUP_TABLE[i] = value;
        }
    }

    private void slide(byte inByte) {
        byte outByte = window[windowPos];
        window[windowPos] = inByte;
        windowPos = (windowPos + 1) % WINDOW_SIZE;

        // Update rolling fingerprint
        fingerprint = ((fingerprint << 1) | (fingerprint >>> 63)); // Rotate left
        fingerprint ^= LOOKUP_TABLE[outByte & 0xFF];  // Remove outgoing byte
        fingerprint ^= LOOKUP_TABLE[inByte & 0xFF];   // Add incoming byte
    }

    private boolean isChunkBoundary() {
        return (fingerprint & CHUNK_MASK) == 0;
    }

    public List<byte[]> chunk(byte[] data) {
        List<byte[]> chunks = new ArrayList<>();
        int start = 0;
        fingerprint = 0;
        windowPos = 0;

        // Initialize first window
        for (int i = 0; i < WINDOW_SIZE && i < data.length; i++) {
            window[i] = data[i];
            fingerprint = ((fingerprint << 1) | (fingerprint >>> 63));
            fingerprint ^= LOOKUP_TABLE[data[i] & 0xFF];
        }

        for (int i = WINDOW_SIZE; i < data.length; i++) {
            slide(data[i]);

            int chunkLength = i - start;
            if (chunkLength >= MIN_CHUNK &&
                    (isChunkBoundary() || chunkLength >= MAX_CHUNK)) {
                chunks.add(Arrays.copyOfRange(data, start, i));
                start = i;
            }
        }

        // Add final chunk if needed
        if (start < data.length) {
            chunks.add(Arrays.copyOfRange(data, start, data.length));
        }

        return chunks;
    }

    private static long calculateChunkHash(byte[] chunk) {
        long hash = 0;
        for (byte b : chunk) {
            hash = ((hash << 1) | (hash >>> 63)) ^ b;
            hash *= POLY;
        }
        return hash;
    }

    private static void printChunks(List<byte[]> chunks, String title) {
        System.out.println("\n" + title + " Chunks:");
        System.out.println("----------------------");
        for (int i = 0; i < Math.min(5, chunks.size()); i++) {
            System.out.println("Chunk " + i + ":");
//        System.out.println(new String(chunks.get(i)));
            System.out.println("Size: " + chunks.get(i).length + " bytes");
            // Calculate and print hash for this chunk
            long chunkHash = calculateChunkHash(chunks.get(i));
            System.out.println("Hash: " + String.format("0x%016X", chunkHash));
            System.out.println("----------------------");
        }
    }

}
