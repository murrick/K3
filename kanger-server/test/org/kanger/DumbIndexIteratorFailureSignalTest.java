/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.storage.Index;

import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mid-iteration index I/O failures must not masquerade as end-of-index.
 */
class DumbIndexIteratorFailureSignalTest {

    @Test
    void midIterationReadFailureMustNotBecomeFalseOrNull() throws Exception {
        Path root = Files.createTempDirectory("kanger-dumb-index-iterator-");
        Path file = root.resolve("fixture.index");

        User user = new User();
        Index index = new Index(5, new Object(), user);
        index.setBlockSize(1);
        index.open(file.toFile(), false);
        try {
            index.set(1L, 101L);
            index.set(2L, 202L);
            index.flush();

            Iterator<Index.IndexOne> iterator = index.iterator(false);
            assertTrue(iterator.hasNext(), "fixture index unexpectedly empty");
            Index.IndexOne first = iterator.next();
            assertEquals(1L, first.getId(), "fixture did not start in first index block");

            physicalReader(index).close();

            assertThrows(IllegalStateException.class,
                    iterator::hasNext,
                    "index read failure was silently converted to hasNext=false");
            assertThrows(IllegalStateException.class,
                    iterator::next,
                    "index read failure was silently converted to next=null");
        } finally {
            index.close();
        }
    }

    private RandomAccessFile physicalReader(Index index) throws Exception {
        Field field = Index.class.getDeclaredField("rasRead");
        field.setAccessible(true);
        return (RandomAccessFile) field.get(index);
    }
}
