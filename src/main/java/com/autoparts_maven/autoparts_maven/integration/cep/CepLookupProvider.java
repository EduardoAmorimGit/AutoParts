package com.autoparts_maven.autoparts_maven.integration.cep;

import java.util.Optional;

/** Looks up the official address data of a CEP. */
public interface CepLookupProvider {

    /**
     * @param cep eight digits
     * @return empty when the CEP does not exist
     * @throws CepLookupUnavailableException when the provider cannot answer now
     */
    Optional<CepData> lookup(String cep);
}
