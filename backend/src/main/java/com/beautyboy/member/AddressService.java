package com.beautyboy.member;

import com.beautyboy.common.BusinessException;
import com.beautyboy.common.ErrorCode;
import com.beautyboy.member.dto.AddressRequest;
import com.beautyboy.member.dto.AddressResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 배송지 CRUD와 "기본배송지는 1개" 불변식을 담당한다.
 * 회원의 첫 배송지는 요청값과 무관하게 기본이 되고,
 * 이후 새 기본을 지정/수정하면 기존 기본이 해제된다.
 */
@Service
public class AddressService {

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(Long memberId) {
        return addressRepository.findByMemberId(memberId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(Long memberId, AddressRequest request) {
        boolean hasExisting = !addressRepository.findByMemberId(memberId).isEmpty();
        boolean shouldBeDefault = !hasExisting || request.isDefault();

        if (shouldBeDefault) {
            unmarkExistingDefault(memberId);
        }

        Address address = new Address(memberId, request.receiver(), request.phone(), request.zipcode(),
                request.address1(), request.address2(), request.latitude(), request.longitude(), shouldBeDefault);
        Address saved = addressRepository.save(address);
        return AddressResponse.from(saved);
    }

    @Transactional
    public AddressResponse updateAddress(Long memberId, Long addressId, AddressRequest request) {
        Address address = getOwnedAddress(memberId, addressId);

        List<Address> memberAddresses = addressRepository.findByMemberId(memberId);
        boolean isOnlyAddress = memberAddresses.size() == 1;
        boolean wasDefault = address.isDefault();
        // 회원의 유일한 배송지라면 기본배송지 지정을 해제하는 요청이 와도 불변식을 지키기 위해 기본을 유지한다.
        boolean shouldBeDefault = request.isDefault() || isOnlyAddress;

        if (shouldBeDefault && !wasDefault) {
            unmarkExistingDefault(memberId);
        }

        address.update(request.receiver(), request.phone(), request.zipcode(), request.address1(),
                request.address2(), request.latitude(), request.longitude(), shouldBeDefault);

        // 원래 기본배송지였는데 이번 수정으로 비기본이 되는 경우에만,
        // "기본배송지 1개 보장" 불변식을 지키기 위해 남은 배송지 중 id가 가장 작은 것을 새 기본으로 승격한다.
        if (wasDefault && !shouldBeDefault) {
            memberAddresses.stream()
                    .filter(a -> !a.getId().equals(address.getId()))
                    .min(Comparator.comparing(Address::getId))
                    .ifPresent(promoted -> promoted.markDefault(true));
        }

        return AddressResponse.from(address);
    }

    @Transactional
    public void deleteAddress(Long memberId, Long addressId) {
        Address address = getOwnedAddress(memberId, addressId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            // 기본배송지가 삭제되었고 남은 배송지가 있다면, id가 가장 작은 것을 새 기본으로 승격한다.
            addressRepository.findByMemberId(memberId).stream()
                    .min(Comparator.comparing(Address::getId))
                    .ifPresent(promoted -> promoted.markDefault(true));
        }
    }

    private Address getOwnedAddress(Long memberId, Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!address.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return address;
    }

    private void unmarkExistingDefault(Long memberId) {
        addressRepository.findFirstByMemberIdAndIsDefaultTrueOrderByIdDesc(memberId)
                .ifPresent(existing -> existing.markDefault(false));
    }
}
