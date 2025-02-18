package com.shelfsync.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateItemRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}", message = "SKU may contain letters, digits, '.', '_' and '-' (max 64)")
        String sku,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 80) String category,
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @Min(0) int initialStock,
        @Min(0) Integer reorderLevel) {
}
