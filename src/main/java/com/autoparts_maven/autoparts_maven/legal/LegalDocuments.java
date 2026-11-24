package com.autoparts_maven.autoparts_maven.legal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * The store's legal documents (Markdown under {@code classpath:legal/}), filled in with the seller
 * identification. {@code legal.terms-version} names the version customers accept at checkout:
 * change it whenever a document changes, so each order keeps the version it was sold under.
 */
@Service
public class LegalDocuments {

    public record Document(String slug, String title, String version, String markdown) {
    }

    private static final Map<String, String> TITLES = Map.of(
            "politica-de-trocas-devolucoes-e-reembolso", "Política de Trocas, Devoluções e Reembolso",
            "termos-de-uso", "Termos de Uso e Condições de Compra",
            "politica-de-privacidade", "Política de Privacidade");
    private static final List<String> ORDER = List.of(
            "termos-de-uso", "politica-de-trocas-devolucoes-e-reembolso", "politica-de-privacidade");

    private final String version;
    private final Map<String, Document> documents = new LinkedHashMap<>();

    public LegalDocuments(StoreInfo store, @Value("${legal.terms-version}") String version) {
        this.version = version;
        Map<String, String> values = Map.of(
                "{{VERSAO}}", version,
                "{{NOME_LOJA}}", store.name(),
                "{{RAZAO_SOCIAL}}", store.legalName(),
                "{{CNPJ}}", store.cnpj(),
                "{{ENDERECO}}", store.address(),
                "{{EMAIL_SAC}}", store.supportEmail(),
                "{{TELEFONE_SAC}}", store.supportPhone(),
                "{{SITE}}", store.siteUrl());
        for (String slug : ORDER) {
            String text = read("legal/" + slug + ".md");
            for (Map.Entry<String, String> value : values.entrySet()) {
                text = text.replace(value.getKey(), value.getValue());
            }
            if (text.contains("{{")) {
                throw new IllegalStateException("Unknown placeholder left in legal/" + slug + ".md");
            }
            documents.put(slug, new Document(slug, TITLES.get(slug), version, text));
        }
    }

    public String version() {
        return version;
    }

    public List<Document> all() {
        return List.copyOf(documents.values());
    }

    public Optional<Document> get(String slug) {
        return Optional.ofNullable(documents.get(slug));
    }

    private static String read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Legal document missing: " + path, exception);
        }
    }
}
