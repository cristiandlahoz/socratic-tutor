package com.wornux.services.crunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CDebugRequestTest {

    @Test
    void convertsEachQuoteBlockIntoAStdinEntry() {
        var request = new CDebugRequest("", "c17", "main.c", "'hola \"como\" estas'\"42\" ''");

        assertEquals("hola \"como\" estas\n42\n\n", request.stdin());
    }

    @Test
    void rejectsTextOutsideQuoteBlocks() {
        assertThrows(IllegalArgumentException.class,
                () -> new CDebugRequest("", "c17", "main.c", "'hola' mundo"));
    }

    @Test
    void rejectsUnclosedQuoteBlocks() {
        assertThrows(IllegalArgumentException.class,
                () -> new CDebugRequest("", "c17", "main.c", "'hola \"como\" estas\""));
    }
}
