package com.autoparts_maven.autoparts_maven.product;

final class ETags {

    private ETags() {
    }

    /**
     * Reads the version from an {@code If-Match} header ({@code "3"} or {@code W/"3"}).
     * Returns {@code null} when the header is absent or {@code *}; an unparseable value can never
     * match the stored version, so it is rejected as a failed precondition.
     */
    static Long parseVersion(String ifMatch, Long productId) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.trim().equals("*")) {
            return null;
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new ProductVersionMismatchException(productId);
        }
    }
}
