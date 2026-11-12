package com.autoparts_maven.autoparts_maven.integration.cep;

/** The CEP provider is unreachable or answered with an error; the request may be retried later. */
public class CepLookupUnavailableException extends RuntimeException {

    public CepLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
