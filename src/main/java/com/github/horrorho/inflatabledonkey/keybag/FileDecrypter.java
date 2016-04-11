/*
 * The MIT License
 *
 * Copyright 2016 Ahseya.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.horrorho.inflatabledonkey.keybag;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.UUID;
import java.util.function.Function;
import net.jcip.annotations.Immutable;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FileDecrypter.
 *
 * @author Ahseya
 */
@Immutable
public final class FileDecrypter {

    private static final Logger logger = LoggerFactory.getLogger(FileDecrypter.class);

    private static final int BLOCK_LENGTH = 0x1000;

    private static final Function<byte[], BlockStreamDecrypter> BLOCKSTREAMDECRYPTERS
            = key -> {
                BlockDecrypter blockDecrypter = BlockDecrypter.create(key);
                Digest digest = new SHA1Digest();
                return new BlockStreamDecrypter(blockDecrypter, digest, BLOCK_LENGTH);
            };

    public static byte[] decrypt(Path file, byte[] key, long decryptedSize, Path tempFolder) throws IOException {
        BlockStreamDecrypter blockStreamDecrypter = BLOCKSTREAMDECRYPTERS.apply(key);

        return decrypt(file, blockStreamDecrypter, decryptedSize, tempFolder);
    }

    public static byte[]
            decrypt(Path file, BlockStreamDecrypter blockStreamDecrypter, long decryptedSize, Path tempFolder)
            throws IOException {

        Path tempFile = tempFile(file, tempFolder);
        try {
            
            Files.move(file, tempFile, StandardCopyOption.REPLACE_EXISTING);

            byte[] hash = doDecrypt(tempFile, file, blockStreamDecrypter);

            truncate(file, decryptedSize);

            return hash;

        } finally {
            try {
                Files.deleteIfExists(tempFile);

            } catch (IOException ex) {
                logger.warn("-- decrypt() > failed to delete temporary file: ", ex);
            }
        }
    }

    static byte[]
            doDecrypt(Path in, Path out, BlockStreamDecrypter blockStreamDecrypter) throws IOException {

        try (InputStream input = Files.newInputStream(in, READ);
                OutputStream output = Files.newOutputStream(out, CREATE, WRITE, TRUNCATE_EXISTING)) {

            return blockStreamDecrypter.decrypt(input, output);
        }
    }

    static void truncate(Path file, long to) throws IOException {
        if (to == 0) {
            return;
        }

        long size = Files.size(file);

        if (size > to) {
            Files.newByteChannel(file, WRITE)
                    .truncate(to)
                    .close();

            logger.debug("-- truncate() - truncated: {}, {} > {}", file, size, to);

        } else if (size < to) {
            logger.warn("-- truncate() - cannot truncate: {}, {} > {}", file, size, to);
        }
    }

    static Path tempFile(Path file, Path tempFolder) {
        return tempFolder.resolve(UUID.randomUUID().toString());
    }
}
