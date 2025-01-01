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

    private void initLookupTable() {
        // Initialize lookup table for polynomial calculations
        // 1 byte has 256 total values = 2^8 [in binary]
        for (int i = 0; i < 256; i++) {
            long value = i;
            /*
             * 256  128   64   32   16  8  4  2   1
             *  0    0    0    0    0   0  0  0   0  ===> (0) Decimal
             *  0    0    0    0    0   0  0  0   1  ===> (1) Decimal
             *  0    0    0    0    0   0  0  1   0  ===> (2) Decimal
             *  0    0    0    0    0   0  0  1   1  ===> (3) Decimal
             *  .....................................and so on.......
             */
            for (int j = 0; j < 8; j++) { // We process each bit of the byte to generate a unique hash (Since 8 bits in the byte)

                if ((value & 1) == 1) { // Check if the number is even or odd. Assume number 5 (in binary [0101]) now AND of [0101 & 1] is [1]==> So it's odd, if the result is 0 then it's even
                    // Calculation for ODD
                    // Right shift 1 bit and XOR with POLY(0x3)
                    // Doing ^ POLY; ensures good bit distribution
                    value = (value >>> 1) ^ POLY;
                } else {
                    // Calculation for Even
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
        // We just check if last 13 digits are 0
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
            /*
             * // This is kind of rolling hash
             * // Move 1 bit to the left, then move all 63 bits to the right
             * //  and Do OR operation
             * Original:     1101
             * Left shift:   1010  (step 1)
             * Right shift:  0001  (step 2)
             * After OR:     1011  (final result) [So you notice left-Most bit came to the right and rolled to the left]
             */
            fingerprint = ((fingerprint << 1) | (fingerprint >>> 63));
            // Since our LOOKUP TABLE is 255 length
            // So we basically try to get last 8 bits via (data[i] & 0xFF, will give us last 8 bits) and then

            // Why choose XOR operation?
            // because when XOR operation is applied twice it simply negates the operation
            // Assume number is 5 [0101] and if we XOR it with 3 [0011]
            // DO 5 ^ 3 ==> [1001](9) ( In XOR when both are 1 or 0, only then it will produce 1
            // Now reapply [1001] XOR 3[0011] ==> [0101](5)
            // Since it's rolling hash we take XOR with that bit and when we remove it
            // we simply do the XOR again to negate the effect
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

}
