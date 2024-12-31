# DropboxRabinChunker

A Java implementation of content-defined chunking using Rabin fingerprinting algorithm, similar to Dropbox's file synchronization mechanism.

## Overview
This project demonstrates how Dropbox-style chunking works by:
- Using a 48-byte sliding window
- Implementing polynomial-based rolling hash
- Creating content-defined chunk boundaries
- Showing efficient resynchronization after modifications

## Features
- Content-defined chunking with Rabin fingerprinting
- Configurable chunk sizes (2KB - 8KB)
- Efficient lookup table for polynomial calculations
- Chunk comparison and hash visualization
- Resource file handling for testing

## Implementation Details
- Window Size: 48 bytes
- Minimum Chunk Size: 2048 bytes
- Maximum Chunk Size: 8192 bytes
- Polynomial Hash: Uses irreducible polynomial (0x3)
- Lookup Table: 256-entry optimization for hash calculation

## Project Structure
```
src/
├── main/
│   ├── java/
│   │   └── com.byteproxy/
│   │       └── RabinChunker.java
│   └── resources/
│       ├── original.txt
│       └── modified.txt
└── test/
```

## Usage
1. Place your test files in the resources directory
2. Run the RabinChunker class
3. Observe chunk boundaries and hash values
4. See how modifications affect only nearby chunks

## Example Output
```
Original File Chunks:
----------------------
Chunk 0:
Size: 4096 bytes
Hash: 0x1234567890ABCDEF
----------------------

Modified File Chunks:
----------------------
Chunk 0:
Size: 4096 bytes
Hash: 0x9876543210FEDCBA
----------------------

Resynchronization occurred at chunk: 1
```

## Technical Details
- Uses rolling hash for efficient window sliding
- Implements polynomial arithmetic using lookup table
- Maintains chunk size constraints
- Shows chunk fingerprints for comparison

## Dummy Content
- https://www.gutenberg.org/cache/epub/1184/pg1184.txt

## License
MIT License