-- Each installment of a card purchase is a financial_movement linked to its purchase and invoice
-- (E9b). The three columns are all null for an ordinary movement and all set for an installment.
-- The installment description is the purchase description (up to 100) plus " (k/n)".
ALTER TABLE financial_movement
    MODIFY COLUMN description VARCHAR(120) NOT NULL COMMENT 'Financial transaction description',
    ADD COLUMN card_purchase_id BIGINT NULL COMMENT 'Card purchase this installment belongs to',
    ADD COLUMN card_invoice_id  BIGINT NULL COMMENT 'Card invoice this installment falls in',
    ADD COLUMN installment_number INT NULL COMMENT 'Installment number k of the purchase (1-based)',
    ADD CONSTRAINT fk_financial_movement_card_purchase FOREIGN KEY (card_purchase_id) REFERENCES card_purchase (id),
    ADD CONSTRAINT fk_financial_movement_card_invoice FOREIGN KEY (card_invoice_id) REFERENCES card_invoice (id),
    ADD CONSTRAINT chk_financial_movement_card_link CHECK (
        (card_purchase_id IS NULL AND card_invoice_id IS NULL AND installment_number IS NULL)
        OR (card_purchase_id IS NOT NULL AND card_invoice_id IS NOT NULL AND installment_number >= 1)),
    ADD INDEX idx_financial_movement_card_purchase (card_purchase_id),
    ADD INDEX idx_financial_movement_card_invoice (card_invoice_id);
