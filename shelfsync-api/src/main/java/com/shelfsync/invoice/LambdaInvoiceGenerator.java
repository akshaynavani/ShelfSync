package com.shelfsync.invoice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shelfsync.config.ShelfSyncProperties;
import com.shelfsync.invoice.core.InvoiceRequest;
import com.shelfsync.invoice.core.InvoiceResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

/** Delegates rendering and upload to the {@code invoice-lambda} function (synchronous invoke). */
@Component
@ConditionalOnProperty(prefix = "shelfsync.invoice", name = "generator", havingValue = "lambda", matchIfMissing = true)
public class LambdaInvoiceGenerator implements InvoiceGenerator {

    private final LambdaClient lambda;
    private final ObjectMapper mapper;
    private final String functionName;

    public LambdaInvoiceGenerator(LambdaClient lambda, ObjectMapper mapper, ShelfSyncProperties properties) {
        this.lambda = lambda;
        this.mapper = mapper;
        this.functionName = properties.invoice().lambdaFunction();
    }

    @Override
    public InvoiceResult generate(InvoiceRequest request) {
        byte[] payload;
        try {
            payload = mapper.writeValueAsBytes(request);
        } catch (JsonProcessingException e) {
            throw new InvoiceGenerationException("Could not serialise invoice " + request.getInvoiceNumber(), e);
        }

        InvokeResponse response = lambda.invoke(InvokeRequest.builder()
                .functionName(functionName)
                .invocationType(InvocationType.REQUEST_RESPONSE)
                .payload(SdkBytes.fromByteArray(payload))
                .build());

        String body = response.payload() == null ? "" : response.payload().asUtf8String();
        if (response.functionError() != null) {
            throw new InvoiceGenerationException("Lambda " + functionName + " failed (" + response.functionError()
                    + "): " + body);
        }
        try {
            InvoiceResult result = mapper.readValue(body, InvoiceResult.class);
            if (result.getKey() == null) {
                throw new InvoiceGenerationException("Lambda returned no S3 key: " + body);
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new InvoiceGenerationException("Unexpected Lambda response: " + body, e);
        }
    }
}
