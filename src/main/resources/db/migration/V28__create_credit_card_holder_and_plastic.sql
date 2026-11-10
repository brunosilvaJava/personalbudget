-- The account behind a credit card remains the only owner of its limit and invoices. Holders
-- identify people and plastics identify the physical/virtual numbers used by those people.
CREATE TABLE credit_card_holder
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    code               BINARY(16)  NOT NULL,
    credit_card_id     BIGINT      NOT NULL,
    name               VARCHAR(80) NOT NULL,
    kind               VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    titular_slot       BIGINT GENERATED ALWAYS AS
        (CASE WHEN kind = 'TITULAR' THEN credit_card_id END) STORED,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_date       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_credit_card_holder_code UNIQUE (code),
    CONSTRAINT uk_credit_card_holder_titular UNIQUE (titular_slot),
    CONSTRAINT uk_credit_card_holder_name UNIQUE (credit_card_id, name),
    CONSTRAINT fk_credit_card_holder_card FOREIGN KEY (credit_card_id) REFERENCES credit_card (id),
    CONSTRAINT chk_credit_card_holder_kind CHECK (kind IN ('TITULAR', 'ADICIONAL')),
    CONSTRAINT chk_credit_card_holder_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) COMMENT 'People identified on a credit card';

CREATE TABLE credit_card_plastic
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    code               BINARY(16)  NOT NULL,
    credit_card_id     BIGINT      NOT NULL,
    holder_id          BIGINT      NOT NULL,
    last_digits        VARCHAR(4)  NOT NULL,
    kind               VARCHAR(20) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_date       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_credit_card_plastic_code UNIQUE (code),
    CONSTRAINT uk_credit_card_plastic_digits UNIQUE (credit_card_id, last_digits),
    CONSTRAINT fk_credit_card_plastic_card FOREIGN KEY (credit_card_id) REFERENCES credit_card (id),
    CONSTRAINT fk_credit_card_plastic_holder FOREIGN KEY (holder_id) REFERENCES credit_card_holder (id),
    CONSTRAINT chk_credit_card_plastic_digits CHECK (last_digits REGEXP '^[0-9]{4}$'),
    CONSTRAINT chk_credit_card_plastic_kind CHECK (kind IN ('PHYSICAL', 'VIRTUAL', 'UNKNOWN')),
    CONSTRAINT chk_credit_card_plastic_status CHECK (status IN ('ACTIVE', 'REPLACED', 'INACTIVE'))
) COMMENT 'Physical or virtual plastics that share a credit card account';

-- Existing cards get a durable, renameable titular. UUID() is converted to the BINARY(16)
-- representation used throughout this schema.
INSERT INTO credit_card_holder (code, credit_card_id, name, kind, status, version, created_date, last_modified_date)
SELECT UNHEX(REPLACE(UUID(), '-', '')), id, 'Titular', 'TITULAR', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM credit_card;
