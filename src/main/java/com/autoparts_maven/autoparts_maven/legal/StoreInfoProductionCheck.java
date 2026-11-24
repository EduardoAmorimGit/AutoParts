package com.autoparts_maven.autoparts_maven.legal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Selling online without showing the company name, CNPJ, address and a contact channel breaks
 * Decreto 7.962/2013 (art. 2), so under the prod profile the application refuses to start.
 */
@Component
class StoreInfoProductionCheck implements ApplicationRunner {

    private final StoreInfo storeInfo;
    private final Environment environment;

    StoreInfoProductionCheck(StoreInfo storeInfo, Environment environment) {
        this.storeInfo = storeInfo;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("prod")) && !storeInfo.isComplete()) {
            throw new IllegalStateException(
                    "STORE_LEGAL_NAME, STORE_CNPJ, STORE_ADDRESS and STORE_SUPPORT_EMAIL are required in production");
        }
    }
}
