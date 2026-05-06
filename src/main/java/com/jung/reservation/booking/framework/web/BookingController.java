package com.jung.reservation.booking.framework.web;

import com.jung.reservation.booking.application.usecase.BookingUseCase;
import com.jung.reservation.booking.framework.web.dto.BookingRequest;
import com.jung.reservation.booking.framework.web.dto.BookingResponse;
import com.jung.reservation.common.exception.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookingController {

    private final BookingUseCase bookingUseCase;

    @PostMapping("/booking")
    public ResponseEntity<SuccessResponse<BookingResponse>> booking(
            @RequestBody BookingRequest request) {
        BookingResponse response = bookingUseCase.book(request);
        return ResponseEntity.ok(SuccessResponse.success(response));
    }
}
