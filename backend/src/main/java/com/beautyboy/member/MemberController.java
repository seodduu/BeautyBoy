package com.beautyboy.member;

import com.beautyboy.common.ApiResponse;
import com.beautyboy.member.dto.AddressRequest;
import com.beautyboy.member.dto.AddressResponse;
import com.beautyboy.member.dto.MemberMeResponse;
import com.beautyboy.member.dto.MemberResponse;
import com.beautyboy.member.dto.ProfileRequest;
import com.beautyboy.member.dto.SignupRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MemberController {

    private final MemberService memberService;
    private final AddressService addressService;

    public MemberController(MemberService memberService, AddressService addressService) {
        this.memberService = memberService;
        this.addressService = addressService;
    }

    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody SignupRequest request) {
        MemberResponse response = memberService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/api/v1/members/me")
    public ResponseEntity<ApiResponse<MemberMeResponse>> me(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.getMe(memberId)));
    }

    @PutMapping("/api/v1/members/me/profile")
    public ResponseEntity<ApiResponse<MemberMeResponse>> updateProfile(@AuthenticationPrincipal Long memberId,
                                                                         @Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.updateProfile(memberId, request)));
    }

    @GetMapping("/api/v1/members/me/addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> addresses(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(addressService.getAddresses(memberId)));
    }

    @PostMapping("/api/v1/members/me/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(@AuthenticationPrincipal Long memberId,
                                                                        @Valid @RequestBody AddressRequest request) {
        AddressResponse response = addressService.createAddress(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PutMapping("/api/v1/members/me/addresses/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(@AuthenticationPrincipal Long memberId,
                                                                        @PathVariable Long id,
                                                                        @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(addressService.updateAddress(memberId, id, request)));
    }

    @DeleteMapping("/api/v1/members/me/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal Long memberId, @PathVariable Long id) {
        addressService.deleteAddress(memberId, id);
        return ResponseEntity.noContent().build();
    }
}
