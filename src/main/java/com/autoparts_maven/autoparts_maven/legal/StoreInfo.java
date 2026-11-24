package com.autoparts_maven.autoparts_maven.legal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Identification of the seller that the law (Decreto 7.962/2013, art. 2) requires on the site and
 * that e-mails and legal documents show. Values come from {@code store.*} properties.
 */
@Component
public class StoreInfo {

    private final String name;
    private final String legalName;
    private final String cnpj;
    private final String address;
    private final String supportEmail;
    private final String supportPhone;
    private final String siteUrl;

    public StoreInfo(
            @Value("${store.name:AutoParts}") String name,
            @Value("${store.legal-name:}") String legalName,
            @Value("${store.cnpj:}") String cnpj,
            @Value("${store.address:}") String address,
            @Value("${store.support-email:}") String supportEmail,
            @Value("${store.support-phone:}") String supportPhone,
            @Value("${store.site-url:}") String siteUrl) {
        this.name = name;
        this.legalName = legalName;
        this.cnpj = cnpj;
        this.address = address;
        this.supportEmail = supportEmail;
        this.supportPhone = supportPhone;
        this.siteUrl = siteUrl;
    }

    public String name() {
        return name;
    }

    public String legalName() {
        return orPending(legalName);
    }

    public String cnpj() {
        return orPending(cnpj);
    }

    public String address() {
        return orPending(address);
    }

    public String supportEmail() {
        return orPending(supportEmail);
    }

    public String supportPhone() {
        return orPending(supportPhone);
    }

    public String siteUrl() {
        return orPending(siteUrl);
    }

    /** True when every field the law requires is filled in (checked at startup under prod). */
    public boolean isComplete() {
        return !legalName.isBlank() && !cnpj.isBlank() && !address.isBlank() && !supportEmail.isBlank();
    }

    private static String orPending(String value) {
        return value == null || value.isBlank() ? "[A PREENCHER]" : value;
    }
}
