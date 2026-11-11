package com.autoparts_maven.autoparts_maven.store;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreSettingsService {

    public record SettingsView(BigDecimal deliveryFee, Long updatedBy, Instant updatedAt) {
    }

    private final StoreSettingsRepository repository;

    @Transactional(readOnly = true)
    public SettingsView get() {
        StoreSettings settings = load();
        return new SettingsView(settings.getDeliveryFee(), settings.getUpdatedBy(), settings.getUpdatedAt());
    }

    /** Empty when the owners have not configured the delivery fee yet. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> deliveryFee() {
        return Optional.ofNullable(load().getDeliveryFee());
    }

    @Transactional
    public SettingsView updateDeliveryFee(BigDecimal fee, Long adminId) {
        StoreSettings settings = load();
        settings.setDeliveryFee(fee);
        settings.setUpdatedBy(adminId);
        repository.flush();
        return new SettingsView(settings.getDeliveryFee(), settings.getUpdatedBy(), settings.getUpdatedAt());
    }

    private StoreSettings load() {
        return repository.findById(StoreSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("store_settings row is missing (migration V10)"));
    }
}
