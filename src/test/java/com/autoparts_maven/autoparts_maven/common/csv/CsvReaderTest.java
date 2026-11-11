package com.autoparts_maven.autoparts_maven.common.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvReaderTest {

    @Test
    void readsSemicolonFileWithBomQuotesAndBlankLines() throws IOException {
        List<CsvReader.Row> rows = read("﻿sku;fonte\r\nA-1;\"Catálogo; pág. 3\"\r\n\r\nB-2;\"diz \"\"oi\"\"\"\r\n");

        assertThat(rows).hasSize(3);
        assertThat(rows.get(1).values()).containsExactly("A-1", "Catálogo; pág. 3");
        assertThat(rows.get(2).line()).isEqualTo(4);
        assertThat(rows.get(2).get(1)).isEqualTo("diz \"oi\"");
    }

    @Test
    void readsCommaFileAndLineBreaksInsideQuotes() throws IOException {
        List<CsvReader.Row> rows = read("a,b\n1,\"two\nlines\"\n3,4");

        assertThat(rows).hasSize(3);
        assertThat(rows.get(1).get(1)).isEqualTo("two\nlines");
        assertThat(rows.get(2).line()).isEqualTo(4);
    }

    @Test
    void missingTrailingColumnsReadAsNull() throws IOException {
        assertThat(read("a;b;c\n1").get(1).get(2)).isNull();
    }

    @Test
    void rejectsUnclosedQuotesAndTooManyRows() {
        assertThatThrownBy(() -> read("a\n\"open")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> CsvReader.read(stream("a\n1\n2\n3"), 3)).isInstanceOf(IOException.class);
    }

    private static List<CsvReader.Row> read(String content) throws IOException {
        return CsvReader.read(stream(content), 100);
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
