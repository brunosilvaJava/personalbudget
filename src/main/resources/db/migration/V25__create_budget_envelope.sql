-- Budget envelopes (E10): the amount planned for a category in one pay cycle. The cycle is linked
-- by its id (its code never changes even when its dates move), so an envelope never follows a
-- change of the configuration to another period.
--
-- active_slot is 1 while the envelope is active and NULL once it is deleted, so UNIQUE
-- (pay_cycle_id, category_code, active_slot) allows one active envelope per category and cycle.
CREATE TABLE budget_envelope
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)     NOT NULL COMMENT 'Envelope uuid code',
    pay_cycle_id       BIGINT         NOT NULL COMMENT 'Cycle the envelope belongs to',
    category_code      BINARY(16)     NOT NULL COMMENT 'Category the amount is planned for (a DEBIT category)',
    planned_amount     DECIMAL(12, 2) NOT NULL COMMENT 'Amount planned for the category in the cycle (positive)',
    flag_active        TINYINT(1)     NOT NULL DEFAULT 1 COMMENT 'Logical exclusion flag',
    active_slot        TINYINT GENERATED ALWAYS AS (CASE WHEN flag_active = 1 THEN 1 END) STORED,
    version            BIGINT         NOT NULL DEFAULT 0 COMMENT 'Optimistic-lock version',
    created_date       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_budget_envelope_code UNIQUE (code),
    CONSTRAINT uk_budget_envelope_active UNIQUE (pay_cycle_id, category_code, active_slot),
    CONSTRAINT fk_budget_envelope_pay_cycle FOREIGN KEY (pay_cycle_id) REFERENCES pay_cycle (id),
    CONSTRAINT chk_budget_envelope_amount CHECK (planned_amount > 0)
) COMMENT 'Budget envelopes per cycle and category';
