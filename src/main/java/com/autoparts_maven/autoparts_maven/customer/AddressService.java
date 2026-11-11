package com.autoparts_maven.autoparts_maven.customer;

import com.autoparts_maven.autoparts_maven.customer.dto.AddressDtos.AddressRequest;
import com.autoparts_maven.autoparts_maven.customer.dto.AddressDtos.AddressResponse;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A customer's own addresses; another user's address always reads as not found. */
@Service
@RequiredArgsConstructor
public class AddressService {

    static final int MAX_ADDRESSES = 10;

    private final AddressRepository addressRepository;
    private final DeliveryArea deliveryArea;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addressRepository.findByUserIdOrderByIdAsc(userId).stream().map(AddressResponse::from).toList();
    }

    @Transactional
    public AddressResponse create(Long userId, AddressRequest request) {
        if (addressRepository.countByUserId(userId) >= MAX_ADDRESSES) {
            throw new AddressLimitException(MAX_ADDRESSES);
        }
        Address address = new Address();
        address.setUserId(userId);
        apply(address, request);
        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(Long userId, Long addressId, AddressRequest request) {
        Address address = getOwned(userId, addressId);
        apply(address, request);
        return AddressResponse.from(address);
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        addressRepository.delete(getOwned(userId, addressId));
    }

    /** For checkout: the owned address, re-checked against the current delivery area. */
    @Transactional(readOnly = true)
    public Address getDeliverable(Long userId, Long addressId) {
        Address address = getOwned(userId, addressId);
        if (!deliveryArea.covers(address.getCep(), address.getCity(), address.getState())) {
            throw new OutsideDeliveryAreaException(deliveryArea.description());
        }
        return address;
    }

    private Address getOwned(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }

    // Only addresses the store can deliver to are accepted, so checkout never surprises the customer.
    private void apply(Address address, AddressRequest request) {
        String cep = digits(request.cep());
        String state = request.state().trim().toUpperCase(Locale.ROOT);
        if (!deliveryArea.covers(cep, request.city(), state)) {
            throw new OutsideDeliveryAreaException(deliveryArea.description());
        }
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
        address.setDistrict(request.district().trim());
        address.setCity(request.city().trim());
        address.setState(state);
        address.setReference(blankToNull(request.reference()));
    }

    private static String digits(String value) {
        return value.replaceAll("\\D", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
