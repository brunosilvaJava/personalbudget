ALTER TABLE card_purchase
    ADD COLUMN card_holder_id BIGINT NULL COMMENT 'Holder who used the card',
    ADD COLUMN card_last_digits VARCHAR(4) NULL COMMENT 'Last four digits of the plastic used',
    ADD CONSTRAINT fk_card_purchase_holder FOREIGN KEY (card_holder_id) REFERENCES credit_card_holder (id),
    ADD CONSTRAINT chk_card_purchase_last_digits CHECK (card_last_digits IS NULL OR card_last_digits REGEXP '^[0-9]{4}$'),
    ADD INDEX idx_card_purchase_holder (card_holder_id);

-- Purchases created before holders existed are purchases by the card titular.
UPDATE card_purchase p
JOIN credit_card_holder h ON h.credit_card_id = p.credit_card_id AND h.kind = 'TITULAR'
SET p.card_holder_id = h.id
WHERE p.card_holder_id IS NULL;
