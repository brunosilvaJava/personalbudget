-- Transfers between accounts (E8b): move money from one account to another.
--
-- A transfer is neutral for consumption and for the category reports: it is not a movement and
-- never reaches /balance/daily, /dashboard, /category_summary or the pay cycles. It only changes
-- the balance of the two accounts. Reversal keeps the record (reversed_at), like a settlement.
CREATE TABLE account_transfer
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)     NOT NULL COMMENT 'Transfer uuid code',
    from_account_id    BIGINT         NOT NULL COMMENT 'Account the money leaves',
    to_account_id      BIGINT         NOT NULL COMMENT 'Account the money enters',
    amount             DECIMAL(10, 2) NOT NULL COMMENT 'Positive transferred amount',
    effective_date     DATE           NOT NULL COMMENT 'Date the transfer took effect',
    description        VARCHAR(100)   NULL COMMENT 'Optional note',
    reversed_at        DATETIME       NULL COMMENT 'Set when the transfer was reversed; the record is kept',
    version            BIGINT         NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_date       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_account_transfer_code UNIQUE (code),
    CONSTRAINT fk_account_transfer_from FOREIGN KEY (from_account_id) REFERENCES account (id),
    CONSTRAINT fk_account_transfer_to FOREIGN KEY (to_account_id) REFERENCES account (id),
    CONSTRAINT chk_account_transfer_positive_amount CHECK (amount > 0),
    CONSTRAINT chk_account_transfer_distinct_accounts CHECK (from_account_id <> to_account_id),
    INDEX idx_account_transfer_from_date (from_account_id, effective_date),
    INDEX idx_account_transfer_to_date (to_account_id, effective_date),
    INDEX idx_account_transfer_effective_date (effective_date)
) COMMENT 'Transfers between accounts';
