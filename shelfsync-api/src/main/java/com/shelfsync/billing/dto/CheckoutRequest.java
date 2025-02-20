package com.shelfsync.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CheckoutRequest(
        @NotBlank @Size(max = 200) String customerName,
        @Email @Size(max = 254) String customerEmail,
        @NotEmpty @Size(max = 100) List<@Valid Line> lines) {

    public record Line(@NotBlank String sku, @Min(1) int quantity) {
    }
}
