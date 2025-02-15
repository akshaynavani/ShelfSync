package com.shelfsync.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Typed view over the {@code shelfsync.*} configuration tree.
 */
@Validated
@ConfigurationProperties(prefix = "shelfsync")
public record ShelfSyncProperties(
        Security security,
        Aws aws,
        DynamoDb dynamodb,
        S3 s3,
        Billing billing,
        Invoice invoice) {

    public record Security(String apiKey) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Aws(@NotBlank String region, String endpointOverride) {
        public boolean hasEndpointOverride() {
            return endpointOverride != null && !endpointOverride.isBlank();
        }
    }

    public record DynamoDb(@NotBlank String inventoryTable) {
    }

    public record S3(@NotBlank String invoiceBucket, Duration presignTtl) {
    }

    public record Billing(
            @NotBlank String currency,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal taxRate,
            @NotBlank String sellerName,
            String sellerAddress) {
    }

    public record Invoice(
            @NotBlank String generator,
            String lambdaFunction,
            Duration retryInterval,
            @Min(1) int maxAttempts) {
    }
}
