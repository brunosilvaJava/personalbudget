-- Card purchases and invoices (E9b).
--
-- A purchase is split into installments; each installment is a financial_movement (V23) that
-- belongs to one invoice. An invoice is created the first time an installment falls in it and is
-- unique per card and reference month (the first day of the month the invoice closes in).
-- The invoice state (OPEN, CLOSED, PAID) is derived, never stored.
CREATE TABLE card_invoice
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16) NOT NULL COMMENT 'Invoice uuid code',
    credit_card_id     BIGINT     NOT NULL COMMENT 'Card the invoice belongs to',
    reference_month    DATE       NOT NULL COMMENT 'First day of the month the invoice closes in',
    closing_date       DATE       NOT NULL COMMENT 'Date the invoice closes',
    due_date           DATE       NOT NULL COMMENT 'Date the invoice is due',
    created_date       DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_card_invoice_code UNIQUE (code),
    CONSTRAINT uk_card_invoice_card_month UNIQUE (credit_card_id, reference_month),
    CONSTRAINT fk_card_invoice_card FOREIGN KEY (credit_card_id) REFERENCES credit_card (id),
    CONSTRAINT chk_card_invoice_reference_month CHECK (DAYOFMONTH(reference_month) = 1),
    CONSTRAINT chk_card_invoice_due_after_closing CHECK (due_date >= closing_date)
) COMMENT 'Credit card invoices';

CREATE TABLE card_purchase
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)     NOT NULL COMMENT 'Purchase uuid code',
    credit_card_id     BIGINT         NOT NULL COMMENT 'Card used in the purchase',
    description        VARCHAR(100)   NOT NULL COMMENT 'What was bought',
    purchase_date      DATE           NOT NULL COMMENT 'Date of the purchase; decides the first invoice',
    installments       INT            NOT NULL COMMENT 'Number of installments (1 to 60)',
    installment_amount DECIMAL(10, 2) NOT NULL COMMENT 'Positive amount of each installment (same precision as a movement amount)',
    category_code      BINARY(16)     NULL COMMENT 'Optional DEBIT category',
    flag_active        TINYINT(1)     NOT NULL DEFAULT 1 COMMENT 'False after the soft delete',
    version            BIGINT         NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_date       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_card_purchase_code UNIQUE (code),
    CONSTRAINT fk_card_purchase_card FOREIGN KEY (credit_card_id) REFERENCES credit_card (id),
    CONSTRAINT chk_card_purchase_installments CHECK (installments BETWEEN 1 AND 60),
    CONSTRAINT chk_card_purchase_amount CHECK (installment_amount > 0),
    INDEX idx_card_purchase_card_active (credit_card_id, flag_active),
    INDEX idx_card_purchase_date (purchase_date)
) COMMENT 'Credit card purchases';
