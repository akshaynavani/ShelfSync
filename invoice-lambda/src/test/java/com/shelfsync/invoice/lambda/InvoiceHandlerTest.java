package com.shelfsync.invoice.lambda;

import com.shelfsync.invoice.core.InvoicePdfRenderer;
import com.shelfsync.invoice.core.InvoiceRequest;
import com.shelfsync.invoice.core.InvoiceResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InvoiceHandlerTest {

    @Test
    void rendersAndUploadsToConfiguredBucket() {
        S3Client s3 = mock(S3Client.class);
        InvoiceHandler handler = new InvoiceHandler(s3, "test-bucket", new InvoicePdfRenderer());

        InvoiceRequest request = new InvoiceRequest();
        request.setInvoiceNumber("INV-20260301-000007");
        request.setOrderNumber("SO-20260301-ZZZ999");
        request.setIssuedAt("2026-03-01T12:00:00Z");
        request.setCurrency("USD");
        request.setCustomer(new InvoiceRequest.Party("Grace Hopper", null, null));
        request.getLines().add(new InvoiceRequest.Line("BOOK-1", "Notebook", 3, new BigDecimal("2.00"), new BigDecimal("6.00")));
        request.setTotal(new BigDecimal("6.00"));

        InvoiceResult result = handler.handleRequest(request, null);

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(put.getValue().key()).isEqualTo("invoices/2026/03/INV-20260301-000007.pdf");
        assertThat(put.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(result.getKey()).isEqualTo(put.getValue().key());
        assertThat(result.getSizeBytes()).isPositive();
    }
}
