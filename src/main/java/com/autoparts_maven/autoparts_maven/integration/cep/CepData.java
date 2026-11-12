package com.autoparts_maven.autoparts_maven.integration.cep;

/** Official data of a CEP. {@code district} may be blank for CEPs that cover a whole locality. */
public record CepData(String cep, String street, String district, String city, String state) {
}
