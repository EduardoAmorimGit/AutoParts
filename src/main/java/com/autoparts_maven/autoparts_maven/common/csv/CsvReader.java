package com.autoparts_maven.autoparts_maven.common.csv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal RFC 4180 reader for spreadsheet exports: UTF-8 (BOM tolerated), comma or semicolon
 * delimiter (Excel in pt-BR uses ';'), quoted fields with "" escapes and line breaks inside quotes.
 */
public final class CsvReader {

    /** One record; {@code line} is the 1-based line where it starts, for error messages. */
    public record Row(int line, List<String> values) {

        public String get(int index) {
            return index >= 0 && index < values.size() ? values.get(index) : null;
        }
    }

    private CsvReader() {
    }

    /** Header key: uppercase, no accents, words separated by one space ("preço_unitário" -> "PRECO UNITARIO"). */
    public static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return Normalizer.normalize(header, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    public static List<Row> read(InputStream input, int maxRows) throws IOException {
        String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        if (content.startsWith("﻿")) {
            content = content.substring(1);
        }
        char delimiter = detectDelimiter(content);

        List<Row> rows = new ArrayList<>();
        List<String> values = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int rowStart = 1;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
            } else if (c == delimiter) {
                values.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++;
                }
                values.add(field.toString());
                field.setLength(0);
                addIfNotBlank(rows, rowStart, values, maxRows);
                values = new ArrayList<>();
                line++;
                rowStart = line;
            } else {
                field.append(c);
            }
        }
        if (quoted) {
            throw new IOException("Unclosed quoted field starting at line " + rowStart);
        }
        if (!field.isEmpty() || !values.isEmpty()) {
            values.add(field.toString());
            addIfNotBlank(rows, rowStart, values, maxRows);
        }
        return rows;
    }

    private static void addIfNotBlank(List<Row> rows, int line, List<String> values, int maxRows) throws IOException {
        if (values.stream().allMatch(String::isBlank)) {
            return;
        }
        if (rows.size() >= maxRows) {
            throw new IOException("File has more than " + (maxRows - 1) + " data rows");
        }
        rows.add(new Row(line, List.copyOf(values)));
    }

    private static char detectDelimiter(String content) {
        int end = content.indexOf('\n');
        String header = end < 0 ? content : content.substring(0, end);
        long semicolons = header.chars().filter(c -> c == ';').count();
        long commas = header.chars().filter(c -> c == ',').count();
        return semicolons >= commas && semicolons > 0 ? ';' : ',';
    }
}
