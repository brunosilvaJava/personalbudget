-- Payment of a card invoice (E9c). Paying the invoice settles every installment in it, in one
-- account, on one date; each of those settlements points back to the payment, so the payment can
-- be reversed as a whole. A payment is never deleted: reversing it fills reversed_at.
--
-- active_slot is 1 while the payment is not reversed and NULL afterwards, so UNIQUE
-- (card_invoice_id, active_slot) allows one active payment per invoice and any number of reversed ones.
CREATE TABLE card_invoice_payment
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)     NOT NULL COMMENT 'Payment uuid code',
    card_invoice_id    BIGINT         NOT NULL COMMENT 'Invoice that was paid',
    account_id         BIGINT         NOT NULL COMMENT 'Account the money came from',
    payment_date       DATE           NOT NULL COMMENT 'Date the payment took effect',
    amount             DECIMAL(12, 2) NOT NULL COMMENT 'Total paid (positive)',
    reversed_at        DATETIME       NULL COMMENT 'When the payment was reversed; null while active',
    active_slot        TINYINT GENERATED ALWAYS AS (CASE WHEN reversed_at IS NULL THEN 1 END) STORED,
    version            BIGINT         NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    created_date       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_card_invoice_payment_code UNIQUE (code),
    CONSTRAINT uk_card_invoice_payment_active UNIQUE (card_invoice_id, active_slot),
    CONSTRAINT fk_card_invoice_payment_invoice FOREIGN KEY (card_invoice_id) REFERENCES card_invoice (id),
    CONSTRAINT fk_card_invoice_payment_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT chk_card_invoice_payment_amount CHECK (amount > 0)
) COMMENT 'Card invoice payments';

ALTER TABLE movement_settlement
    ADD COLUMN invoice_payment_id BIGINT NULL COMMENT 'Invoice payment that generated this settlement',
    ADD CONSTRAINT fk_movement_settlement_invoice_payment FOREIGN KEY (invoice_payment_id)
        REFERENCES card_invoice_payment (id),
    ADD INDEX idx_movement_settlement_invoice_payment (invoice_payment_id);
