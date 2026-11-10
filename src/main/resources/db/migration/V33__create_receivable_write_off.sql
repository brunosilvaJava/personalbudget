-- Write-off of a receivable: a third party that will not pay. The amount becomes the user's own expense,
-- recognized on the write-off date, always in one fixed category ("Perdas com terceiros"). No cash moves.
--
-- The category has a fixed code so the application finds it even after a rename. Each write-off is its
-- own row (amount, date, category) because the expense belongs to the competence of its own date.
-- financial_movement.written_off_amount stays as the running total used to compute the open balance.
INSERT INTO category (code, name, operation_type, status, category_group_id)
SELECT UUID_TO_BIN('6f1d2c40-8a3e-4b7a-9c55-0d3e7a1b2c33'), 'Perdas com terceiros', 'DEBIT', 'ACTIVE', category_group.id
FROM category_group
WHERE category_group.name = 'Outros'
  AND NOT EXISTS (
    SELECT 1
    FROM category
    WHERE category.code = UUID_TO_BIN('6f1d2c40-8a3e-4b7a-9c55-0d3e7a1b2c33')
       OR (category.name = 'Perdas com terceiros' AND category.operation_type = 'DEBIT')
);

CREATE TABLE receivable_write_off
(
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code                   BINARY(16)     NOT NULL COMMENT 'Write-off uuid code',
    receivable_movement_id BIGINT         NOT NULL COMMENT 'The receivable (financial_movement) that was written off',
    category_id            BIGINT         NOT NULL COMMENT 'Category of the expense the write-off became',
    amount                 DECIMAL(10, 2) NOT NULL COMMENT 'Amount written off, positive',
    effective_date         DATE           NOT NULL COMMENT 'Competence of the expense',
    created_date           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_receivable_write_off_code UNIQUE (code),
    CONSTRAINT fk_receivable_write_off_receivable FOREIGN KEY (receivable_movement_id) REFERENCES financial_movement (id),
    CONSTRAINT fk_receivable_write_off_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT chk_receivable_write_off_amount CHECK (amount > 0),
    INDEX idx_receivable_write_off_effective_date (effective_date),
    INDEX idx_receivable_write_off_receivable (receivable_movement_id)
) COMMENT 'Receivables written off, each one an own expense';

-- Write-offs recorded before this table existed keep their amount and date.
INSERT INTO receivable_write_off (code, receivable_movement_id, category_id, amount, effective_date)
SELECT UNHEX(REPLACE(UUID(), '-', '')), financial_movement.id, category.id,
       financial_movement.written_off_amount, financial_movement.written_off_date
FROM financial_movement
JOIN category ON category.code = UUID_TO_BIN('6f1d2c40-8a3e-4b7a-9c55-0d3e7a1b2c33')
WHERE financial_movement.written_off_amount IS NOT NULL
  AND financial_movement.written_off_amount > 0;
