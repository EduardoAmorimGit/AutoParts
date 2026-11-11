package com.autoparts_maven.autoparts_maven.compatibility;

final class CompatibilityExceptions {

    private CompatibilityExceptions() {
    }

    static class SpecNotFoundException extends RuntimeException {
        SpecNotFoundException(Long id) {
            super("Vehicle spec with id " + id + " not found");
        }
    }

    static class DuplicateSpecException extends RuntimeException {
        DuplicateSpecException() {
            super("An identical vehicle spec already exists");
        }
    }

    static class FitmentNotFoundException extends RuntimeException {
        FitmentNotFoundException(Long id) {
            super("Fitment with id " + id + " not found");
        }
    }

    static class InvalidImportFileException extends RuntimeException {
        InvalidImportFileException(String message) {
            super(message);
        }
    }

    static class InvalidSpecException extends RuntimeException {
        InvalidSpecException(String message) {
            super(message);
        }
    }
}
