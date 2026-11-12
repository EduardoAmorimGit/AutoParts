package com.autoparts_maven.autoparts_maven.support;

import com.autoparts_maven.autoparts_maven.integration.cep.CepData;
import com.autoparts_maven.autoparts_maven.integration.cep.CepLookupProvider;
import com.autoparts_maven.autoparts_maven.integration.cep.CepLookupUnavailableException;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Fixed CEPs for tests that save addresses; the real provider is never called in tests. */
@TestConfiguration
public class CepLookupTestConfig {

    /** Makes the stub fail as if the provider were down. */
    public static final String UNAVAILABLE_CEP = "20999000";

    private static final Map<String, CepData> CEPS = Map.of(
            "20040020", rio("20040020", "Rua do Ouvidor", "Centro"),
            "22041001", rio("22041001", "Avenida Atlântica", "Copacabana"),
            "21810000", rio("21810000", "Rua Francisco Real", "Bangu"),
            "23050000", rio("23050000", "Estrada do Mendanha", "Campo Grande"),
            "20000999", rio("20000999", "", ""),
            "24020000", new CepData("24020000", "Rua da Conceição", "Centro", "Niterói", "RJ"),
            "01001000", new CepData("01001000", "Praça da Sé", "Sé", "São Paulo", "SP"));

    @Bean
    @Primary
    CepLookupProvider stubCepLookupProvider() {
        return cep -> {
            if (UNAVAILABLE_CEP.equals(cep)) {
                throw new CepLookupUnavailableException("stub provider down", null);
            }
            return Optional.ofNullable(CEPS.get(cep));
        };
    }

    private static CepData rio(String cep, String street, String district) {
        return new CepData(cep, street, district, "Rio de Janeiro", "RJ");
    }
}
