package com.shelfsync.invoice;

import com.shelfsync.config.ShelfSyncProperties;
import com.shelfsync.invoice.core.InvoiceKeys;
import com.shelfsync.invoice.core.InvoicePdfRenderer;
import com.shelfsync.invoice.core.InvoiceRequest;
import com.shelfsync.invoice.core.InvoiceResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Renders in-process with the same {@code invoice-core} renderer the Lambda uses, then uploads to
 * S3 directly. Handy for local development against LocalStack without deploying the function.
 */
@Component
@ConditionalOnProperty(prefix = "shelfsync.invoice", name = "generator", havingValue = "local")
public class LocalInvoiceGenerator implements InvoiceGenerator {

    private final S3Client s3;
    private final String bucket;
    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    public LocalInvoiceGenerator(S3Client s3, ShelfSyncProperties properties) {
        this.s3 = s3;
        this.bucket = properties.s3().invoiceBucket();
    }

    @Override
    public InvoiceResult generate(InvoiceRequest request) {
        byte[] pdf = renderer.render(request);
        String key = InvoiceKeys.keyFor(request);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("application/pdf")
                        .build(),
                RequestBody.fromBytes(pdf));
        return new InvoiceResult(bucket, key, pdf.length);
    }
}
