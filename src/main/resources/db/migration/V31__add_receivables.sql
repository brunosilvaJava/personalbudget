-- A receivable is an ordinary CREDIT movement, linked to its source installment but deliberately
-- without the three card-link columns. It therefore never consumes card limit or looks like an
-- installment to generic card rules.
ALTER TABLE financial_movement
    ADD COLUMN receivable_source_movement_id BIGINT NULL,
    ADD COLUMN written_off_amount DECIMAL(10,2) NULL,
    ADD COLUMN written_off_date DATE NULL,
    ADD CONSTRAINT fk_financial_movement_receivable_source
        FOREIGN KEY (receivable_source_movement_id) REFERENCES financial_movement (id),
    ADD CONSTRAINT uk_financial_movement_receivable_source UNIQUE (receivable_source_movement_id),
    ADD CONSTRAINT chk_financial_movement_receivable CHECK (
        receivable_source_movement_id IS NULL
        OR (third_party_id IS NOT NULL AND card_purchase_id IS NULL
            AND card_invoice_id IS NULL AND installment_number IS NULL)
    ),
    ADD CONSTRAINT chk_financial_movement_written_off CHECK (
        (written_off_amount IS NULL AND written_off_date IS NULL)
        OR (written_off_amount IS NOT NULL AND written_off_amount >= 0 AND written_off_date IS NOT NULL)
    ),
    ADD INDEX idx_financial_movement_receivable_source (receivable_source_movement_id);
