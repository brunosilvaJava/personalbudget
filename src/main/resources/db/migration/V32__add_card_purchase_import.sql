ALTER TABLE card_purchase
    ADD COLUMN import_key VARCHAR(64) NULL COMMENT 'Idempotency hash used by batch card imports',
    ADD COLUMN first_installment TINYINT NOT NULL DEFAULT 1 COMMENT 'First installment represented by this import',
    ADD CONSTRAINT uk_card_purchase_import_key UNIQUE (credit_card_id, import_key),
    ADD CONSTRAINT chk_card_purchase_first_installment CHECK (first_installment BETWEEN 1 AND installments);
