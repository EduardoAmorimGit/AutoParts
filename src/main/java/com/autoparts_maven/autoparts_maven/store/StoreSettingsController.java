package com.autoparts_maven.autoparts_maven.store;

import com.autoparts_maven.autoparts_maven.store.StoreSettingsService.SettingsView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (/api/admin/**) store settings. */
@RestController
@RequiredArgsConstructor
public class StoreSettingsController {

    public record SettingsRequest(@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal deliveryFee) {
    }

    private final StoreSettingsService storeSettingsService;

    @GetMapping("/api/admin/settings")
    public SettingsView get() {
        return storeSettingsService.get();
    }

    @PutMapping("/api/admin/settings")
    public SettingsView update(@Valid @RequestBody SettingsRequest request, @AuthenticationPrincipal Jwt jwt) {
        return storeSettingsService.updateDeliveryFee(request.deliveryFee(), Long.valueOf(jwt.getSubject()));
    }
}
