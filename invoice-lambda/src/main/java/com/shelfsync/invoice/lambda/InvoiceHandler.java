package com.shelfsync.invoice.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.shelfsync.invoice.core.InvoiceKeys;
import com.shelfsync.invoice.core.InvoicePdfRenderer;
import com.shelfsync.invoice.core.InvoiceRequest;
import com.shelfsync.invoice.core.InvoiceResult;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.net.URI;
import java.util.Map;

/**
 * Lambda entry point: renders the invoice PDF and stores it in S3.
 * <p>
 * Environment:
 * <ul>
 *   <li>{@code INVOICE_BUCKET} (required) target bucket</li>
 *   <li>{@code AWS_ENDPOINT_OVERRIDE} (optional) e.g. LocalStack</li>
 * </ul>
 * Handler string: {@code com.shelfsync.invoice.lambda.InvoiceHandler::handleRequest}
 */
public class InvoiceHandler implements RequestHandler<InvoiceRequest, InvoiceResult> {

    private final S3Client s3;
    private final String bucket;
    private final InvoicePdfRenderer renderer;

    /** Used by the Lambda runtime; the S3 client is created once per container and reused. */
    public InvoiceHandler() {
        this(defaultClient(System.getenv()), requireEnv(System.getenv(), "INVOICE_BUCKET"), new InvoicePdfRenderer());
    }

    public InvoiceHandler(S3Client s3, String bucket, InvoicePdfRenderer renderer) {
        this.s3 = s3;
        this.bucket = bucket;
        this.renderer = renderer;
    }

    @Override
    public InvoiceResult handleRequest(InvoiceRequest request, Context context) {
        LambdaLogger logger = context != null ? context.getLogger() : null;
        byte[] pdf = renderer.render(request);
        String key = InvoiceKeys.keyFor(request);

        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("application/pdf")
                        .contentDisposition("inline; filename=\"" + request.getInvoiceNumber() + ".pdf\"")
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .metadata(Map.of(
                                "invoice-number", request.getInvoiceNumber(),
                                "order-number", request.getOrderNumber() == null ? "" : request.getOrderNumber()))
                        .build(),
                RequestBody.fromBytes(pdf));

        if (logger != null) {
            logger.log("Stored invoice " + request.getInvoiceNumber() + " at s3://" + bucket + "/" + key
                    + " (" + pdf.length + " bytes)");
        }
        return new InvoiceResult(bucket, key, pdf.length);
    }

    static S3Client defaultClient(Map<String, String> env) {
        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.of(env.getOrDefault("AWS_REGION", "us-east-1")));
        String endpoint = env.get("AWS_ENDPOINT_OVERRIDE");
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        return builder.build();
    }

    private static String requireEnv(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable " + name);
        }
        return value;
    }
}
