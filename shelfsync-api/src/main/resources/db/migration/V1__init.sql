-- ShelfSync transactional history. Live stock lives in DynamoDB; MySQL keeps the ledger.

CREATE TABLE stock_movements (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    sku            VARCHAR(64)  NOT NULL,
    movement_type  VARCHAR(20)  NOT NULL,
    quantity_delta INT          NOT NULL,
    stock_after    INT          NOT NULL,
    reference      VARCHAR(64)  NULL,
    note           VARCHAR(255) NULL,
    created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_movements_sku_created (sku, created_at),
    INDEX idx_movements_created (created_at),
    INDEX idx_movements_reference (reference)
) ENGINE = InnoDB;

CREATE TABLE orders (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_number    VARCHAR(32)   NOT NULL,
    idempotency_key VARCHAR(64)   NULL,
    customer_name   VARCHAR(200)  NOT NULL,
    customer_email  VARCHAR(254)  NULL,
    status          VARCHAR(20)   NOT NULL,
    currency        CHAR(3)       NOT NULL,
    subtotal        DECIMAL(12,2) NOT NULL,
    tax_rate        DECIMAL(5,4)  NOT NULL,
    tax             DECIMAL(12,2) NOT NULL,
    total           DECIMAL(12,2) NOT NULL,
    created_at      TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    cancelled_at    TIMESTAMP(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_number (order_number),
    UNIQUE KEY uk_orders_idempotency (idempotency_key),
    INDEX idx_orders_created (created_at)
) ENGINE = InnoDB;

CREATE TABLE order_lines (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    order_id   BIGINT        NOT NULL,
    sku        VARCHAR(64)   NOT NULL,
    item_name  VARCHAR(200)  NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity   INT           NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_lines_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    INDEX idx_order_lines_sku (sku)
) ENGINE = InnoDB;

CREATE TABLE invoices (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    order_id       BIGINT        NOT NULL,
    invoice_number VARCHAR(32)   NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    s3_key         VARCHAR(512)  NULL,
    attempts       INT           NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000) NULL,
    created_at     TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    generated_at   TIMESTAMP(3)  NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoices_order (order_id),
    UNIQUE KEY uk_invoices_number (invoice_number),
    CONSTRAINT fk_invoices_order FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_invoices_status (status)
) ENGINE = InnoDB;
