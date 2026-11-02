ALTER TABLE financial_movement
    ADD COLUMN category_code BINARY(16) NULL COMMENT 'Category uuid code',
    ADD CONSTRAINT fk_financial_movement_category FOREIGN KEY (category_code) REFERENCES category (code);

ALTER TABLE fixed_bill
    ADD COLUMN category_code BINARY(16) NULL COMMENT 'Category uuid code',
    ADD CONSTRAINT fk_fixed_bill_category FOREIGN KEY (category_code) REFERENCES category (code);

ALTER TABLE installment_bill
    ADD COLUMN category_code BINARY(16) NULL COMMENT 'Category uuid code',
    ADD CONSTRAINT fk_installment_bill_category FOREIGN KEY (category_code) REFERENCES category (code);
