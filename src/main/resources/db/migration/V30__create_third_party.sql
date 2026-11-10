CREATE TABLE third_party
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    code               BINARY(16)  NOT NULL,
    name               VARCHAR(80) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_date       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_third_party_code UNIQUE (code),
    CONSTRAINT uk_third_party_name UNIQUE (name),
    CONSTRAINT chk_third_party_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) COMMENT 'People who reimburse card purchases';

ALTER TABLE credit_card
    ADD COLUMN default_third_party_id BIGINT NULL,
    ADD CONSTRAINT fk_credit_card_default_third_party
        FOREIGN KEY (default_third_party_id) REFERENCES third_party (id);

ALTER TABLE credit_card_plastic
    ADD COLUMN default_third_party_id BIGINT NULL,
    ADD CONSTRAINT fk_credit_card_plastic_default_third_party
        FOREIGN KEY (default_third_party_id) REFERENCES third_party (id);

ALTER TABLE card_purchase
    ADD COLUMN third_party_id BIGINT NULL,
    ADD CONSTRAINT fk_card_purchase_third_party FOREIGN KEY (third_party_id) REFERENCES third_party (id),
    ADD INDEX idx_card_purchase_third_party (third_party_id);

ALTER TABLE financial_movement
    ADD COLUMN third_party_id BIGINT NULL,
    ADD CONSTRAINT fk_financial_movement_third_party FOREIGN KEY (third_party_id) REFERENCES third_party (id),
    ADD INDEX idx_financial_movement_third_party (third_party_id);
