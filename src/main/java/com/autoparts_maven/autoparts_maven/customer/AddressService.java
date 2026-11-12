package com.autoparts_maven.autoparts_maven.customer;

import com.autoparts_maven.autoparts_maven.customer.dto.AddressDtos.AddressRequest;
import com.autoparts_maven.autoparts_maven.customer.dto.AddressDtos.AddressResponse;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryDtos.DeliveryQuote;
import com.autoparts_maven.autoparts_maven.delivery.DeliveryRegionService;
import com.autoparts_maven.autoparts_maven.delivery.DistrictNotServedException;
import com.autoparts_maven.autoparts_maven.integration.cep.CepData;
import com.autoparts_maven.autoparts_maven.integration.cep.CepLookupProvider;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** A customer's own addresses; another user's address always reads as not found. */
@Service
@RequiredArgsConstructor
public class AddressService {

    static final int MAX_ADDRESSES = 10;

    private final AddressRepository addressRepository;
    private final DeliveryArea deliveryArea;
    private final DeliveryRegionService deliveryRegionService;
    private final CepLookupProvider cepLookupProvider;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addressRepository.findByUserIdOrderByIdAsc(userId).stream().map(this::toResponse).toList();
    }

    // create/update are not @Transactional: the CEP is verified with the provider first, so the HTTP
    // call never holds a database connection; the write runs in its own short transaction.
    public AddressResponse create(Long userId, AddressRequest request) {
        CepData official = verifyCep(request);
        return transactionTemplate.execute(status -> {
            if (addressRepository.countByUserId(userId) >= MAX_ADDRESSES) {
                throw new AddressLimitException(MAX_ADDRESSES);
            }
            Address address = new Address();
            address.setUserId(userId);
            apply(address, request, official);
            return toResponse(addressRepository.save(address));
        });
    }

    public AddressResponse update(Long userId, Long addressId, AddressRequest request) {
        CepData official = verifyCep(request);
        return transactionTemplate.execute(status -> {
            Address address = getOwned(userId, addressId);
            apply(address, request, official);
            return toResponse(address);
        });
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        addressRepository.delete(getOwned(userId, addressId));
    }

    /** An address ready for checkout and the delivery fee that applies to it. */
    public record DeliverableAddress(Address address, DeliveryQuote quote) {
    }

    /**
     * For checkout: the owned address, re-checked against the current delivery area, with the fee
     * of the region serving its district. Refused when no active region serves the district.
     */
    @Transactional(readOnly = true)
    public DeliverableAddress getDeliverable(Long userId, Long addressId) {
        Address address = getOwned(userId, addressId);
        if (!deliveryArea.covers(address.getCep(), address.getCity(), address.getState())) {
            throw new OutsideDeliveryAreaException(deliveryArea.description());
        }
        DeliveryQuote quote = deliveryRegionService.quote(address.getDistrict())
                .orElseThrow(() -> new DistrictNotServedException(address.getDistrict()));
        return new DeliverableAddress(address, quote);
    }

    /** The delivery fee for one of the customer's addresses; empty when its district is not served. */
    @Transactional(readOnly = true)
    public Optional<DeliveryQuote> quote(Long userId, Long addressId) {
        return deliveryRegionService.quote(getOwned(userId, addressId).getDistrict());
    }

    private AddressResponse toResponse(Address address) {
        return AddressResponse.from(address, deliveryRegionService.quote(address.getDistrict()).orElse(null));
    }

    private Address getOwned(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }

    /**
     * The official data of the CEP. District, city and state come from it, never from the
     * customer, so the delivery fee cannot be lowered by typing another district.
     */
    private CepData verifyCep(AddressRequest request) {
        String cep = digits(request.cep());
        CepData official = cepLookupProvider.lookup(cep).orElseThrow(() -> new CepNotFoundException(cep));
        if (!deliveryArea.covers(cep, official.city(), official.state())) {
            throw new OutsideDeliveryAreaException(deliveryArea.description());
        }
        return official;
    }

    // Only addresses the store can deliver to are accepted, so checkout never surprises the customer.
    private void apply(Address address, AddressRequest request, CepData official) {
        String cep = official.cep();
        // A CEP that covers a whole locality has no district: the customer's one is kept.
        String district = official.district().isBlank() ? request.district().trim() : official.district();
        String phone = digits(request.phone());
        if (phone.startsWith("55") && phone.length() > 11) {
            phone = phone.substring(2);
        }
        if (phone.length() < 10 || phone.length() > 11) {
            throw new InvalidAddressException("phone must have area code + number (10 or 11 digits)");
        }
        address.setLabel(blankToNull(request.label()));
        address.setRecipientName(request.recipientName().trim());
        address.setPhone(phone);
        address.setCep(cep);
        address.setStreet(request.street().trim());
        address.setNumber(request.number().trim());
        address.setComplement(blankToNull(request.complement()));
        address.setDistrict(district);
        address.setCity(official.city());
        address.setState(official.state().toUpperCase(Locale.ROOT));
        address.setReference(blankToNull(request.reference()));
    }

    private static String digits(String value) {
        return value.replaceAll("\\D", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
