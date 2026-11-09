-- Credit cards (E9a): registration only. Purchases, installments and invoices come with E9b.
--
-- closing_day and due_day are days of the month (1 to 31); a shorter month uses its last day.
-- credit_limit is optional: without it no limit check is made. default_account_id is the account
-- the invoice is paid from when the payment does not inform one.
CREATE TABLE credit_card
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)     NOT NULL COMMENT 'Card uuid code',
    name               VARCHAR(50)    NOT NULL COMMENT 'Card name, unique',
    closing_day        TINYINT        NOT NULL COMMENT 'Day of the month the invoice closes (1 to 31)',
    due_day            TINYINT        NOT NULL COMMENT 'Day of the month the invoice is due (1 to 31)',
    credit_limit       DECIMAL(12, 2) NULL COMMENT 'Optional credit limit; null means no limit check',
    default_account_id BIGINT         NULL COMMENT 'Optional account the invoice is paid from',
    status             VARCHAR(20)    NOT NULL COMMENT 'ACTIVE or INACTIVE',
    version            BIGINT         NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_date       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_credit_card_code UNIQUE (code),
    CONSTRAINT uk_credit_card_name UNIQUE (name),
    CONSTRAINT fk_credit_card_default_account FOREIGN KEY (default_account_id) REFERENCES account (id),
    CONSTRAINT chk_credit_card_closing_day CHECK (closing_day BETWEEN 1 AND 31),
    CONSTRAINT chk_credit_card_due_day CHECK (due_day BETWEEN 1 AND 31),
    CONSTRAINT chk_credit_card_limit CHECK (credit_limit IS NULL OR credit_limit > 0),
    CONSTRAINT chk_credit_card_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    INDEX idx_credit_card_status (status)
) COMMENT 'Credit cards';
