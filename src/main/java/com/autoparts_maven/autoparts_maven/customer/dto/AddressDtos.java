package com.autoparts_maven.autoparts_maven.customer.dto;

import com.autoparts_maven.autoparts_maven.customer.Address;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.DeliveryQuote;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class AddressDtos {

    private AddressDtos() {
    }

    /** CEP accepts "20040-020" or "20040020"; phone accepts any formatting with 10-11 digits (DDD + number). */
    public record AddressRequest(
            @Size(max = 40) String label,
            @NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Pattern(regexp = "^[\\d()\\s.+-]{10,20}$", message = "must be a phone number with area code")
            String phone,
            @NotBlank @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "must be a CEP like 20040-020") String cep,
            @NotBlank @Size(max = 150) String street,
            @NotBlank @Size(max = 20) String number,
            @Size(max = 100) String complement,
            @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a two-letter state") String state,
            @Size(max = 200) String reference) {
    }

    /** Delivery region and fee of an address district; null in {@link AddressResponse} when not served yet. */
    public record AddressDelivery(String region, BigDecimal fee) {
    }

    public record AddressResponse(Long id, String label, String recipientName, String phone, String cep,
            String street, String number, String complement, String district, String city, String state,
            String reference, AddressDelivery delivery) {

        public static AddressResponse from(Address address, DeliveryQuote quote) {
            return new AddressResponse(address.getId(), address.getLabel(), address.getRecipientName(),
                    address.getPhone(), address.getCep(), address.getStreet(), address.getNumber(),
                    address.getComplement(), address.getDistrict(), address.getCity(), address.getState(),
                    address.getReference(), quote == null ? null : new AddressDelivery(quote.region(), quote.fee()));
        }
    }
}
