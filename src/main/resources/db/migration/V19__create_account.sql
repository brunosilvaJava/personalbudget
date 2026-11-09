-- Accounts (E8a): where the money of a settlement came from or went to.
--
-- The initial balance is NOT income: it is the balance the account already had at
-- initial_balance_date. Exactly one account is the default one; the single-slot generated column
-- plus UNIQUE guarantees it (NULLs do not collide in a UNIQUE key).
CREATE TABLE account
(
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code                 BINARY(16)     NOT NULL COMMENT 'Account uuid code',
    name                 VARCHAR(50)    NOT NULL COMMENT 'Account name, unique',
    type                 VARCHAR(20)    NOT NULL COMMENT 'CHECKING, CASH, SAVINGS or OTHER',
    initial_balance      DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT 'Balance at initial_balance_date (not income)',
    initial_balance_date DATE           NOT NULL COMMENT 'Date the initial balance refers to',
    status               VARCHAR(20)    NOT NULL COMMENT 'ACTIVE or INACTIVE',
    is_default           TINYINT(1)     NOT NULL DEFAULT 0 COMMENT 'True for the account used when none is informed',
    default_slot         TINYINT GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN 1 END) STORED,
    created_date         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_account_code UNIQUE (code),
    CONSTRAINT uk_account_name UNIQUE (name),
    CONSTRAINT uk_account_single_default UNIQUE (default_slot),
    CONSTRAINT chk_account_type CHECK (type IN ('CHECKING', 'CASH', 'SAVINGS', 'OTHER')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_account_default_active CHECK (is_default = 0 OR status = 'ACTIVE')
) COMMENT 'Accounts and wallets';

-- Default account for the settlements that already exist. The date is early enough to precede any
-- settlement; the user adjusts the initial balance and its date afterwards.
INSERT INTO account (code, name, type, initial_balance, initial_balance_date, status, is_default)
VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Conta principal', 'CHECKING', 0, '2000-01-01', 'ACTIVE', 1);

ALTER TABLE movement_settlement
    ADD COLUMN account_id BIGINT NULL COMMENT 'Account the payment came from or went to';

UPDATE movement_settlement
SET account_id = (SELECT id FROM account WHERE is_default = 1);

ALTER TABLE movement_settlement
    MODIFY COLUMN account_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_movement_settlement_account FOREIGN KEY (account_id) REFERENCES account (id),
    ADD INDEX idx_movement_settlement_account_date (account_id, effective_date);
