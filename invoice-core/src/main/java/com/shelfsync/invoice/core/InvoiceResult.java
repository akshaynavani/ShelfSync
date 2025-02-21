package com.shelfsync.invoice.core;

/** Where a rendered invoice was stored. JavaBean for Lambda serialisation. */
public class InvoiceResult {

    private String bucket;
    private String key;
    private long sizeBytes;

    public InvoiceResult() {
    }

    public InvoiceResult(String bucket, String key, long sizeBytes) {
        this.bucket = bucket;
        this.key = key;
        this.sizeBytes = sizeBytes;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
