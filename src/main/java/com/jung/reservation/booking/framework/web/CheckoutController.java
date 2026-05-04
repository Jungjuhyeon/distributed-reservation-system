package com.jung.reservation.booking.framework.web;

import com.jung.reservation.booking.application.usecase.CheckoutUseCase;
import com.jung.reservation.booking.framework.web.dto.CheckoutResponse;
import com.jung.reservation.common.exception.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutUseCase checkoutUseCase;

    @GetMapping("/checkout/{roomTypeId}")
    public ResponseEntity<SuccessResponse<CheckoutResponse>> checkout(
            @PathVariable Long roomTypeId,
            @RequestParam Long userId,
            @RequestParam(required = false) Long promotionRoomTypeId) {
        CheckoutResponse response = checkoutUseCase.checkout(roomTypeId, userId, promotionRoomTypeId);
        return ResponseEntity.ok(SuccessResponse.success(response));
    }
}
