-- Validate the legacy payment columns before converting them. A non-zero payment without a date,
-- a date without a payment, or a payment greater than the movement cannot be reconciled safely.
ALTER TABLE financial_movement
    ADD CONSTRAINT chk_financial_movement_settlement_legacy_data
        CHECK (
            (amount_paid IS NULL OR amount_paid = 0 OR pay_date IS NOT NULL)
            AND (pay_date IS NULL OR (amount_paid IS NOT NULL AND amount_paid <> 0))
            AND (amount_paid IS NULL OR ABS(amount_paid) <= ABS(amount))
        );

CREATE TABLE movement_settlement (
    id                      BIGINT          PRIMARY KEY AUTO_INCREMENT,
    code                    BINARY(16)      NOT NULL,
    financial_movement_id   BIGINT          NOT NULL,
    amount                  DECIMAL(10,2)   NOT NULL,
    effective_date          DATE            NOT NULL,
    reversed_at             DATETIME        NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_date            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_movement_settlement_code UNIQUE (code),
    CONSTRAINT fk_movement_settlement_movement
        FOREIGN KEY (financial_movement_id) REFERENCES financial_movement (id),
    CONSTRAINT chk_movement_settlement_positive_amount CHECK (amount > 0),
    INDEX idx_movement_settlement_movement_reversed (financial_movement_id, reversed_at),
    INDEX idx_movement_settlement_effective_date (effective_date)
) COMMENT 'Individual payments applied to financial movements';

INSERT INTO movement_settlement (
    code, financial_movement_id, amount, effective_date, reversed_at, version, created_date, last_modified_date
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    fm.id,
    ABS(fm.amount_paid),
    DATE(fm.pay_date),
    NULL,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM financial_movement fm
WHERE fm.amount_paid IS NOT NULL
  AND fm.amount_paid <> 0
  AND fm.pay_date IS NOT NULL;

ALTER TABLE financial_movement
    DROP CHECK chk_financial_movement_settlement_legacy_data;

UPDATE financial_movement fm
LEFT JOIN (
    SELECT financial_movement_id, SUM(amount) AS paid_amount
    FROM movement_settlement
    WHERE reversed_at IS NULL
    GROUP BY financial_movement_id
) settlements ON settlements.financial_movement_id = fm.id
SET fm.amount_paid = CASE
        WHEN fm.operation_type = 'DEBIT' THEN -COALESCE(settlements.paid_amount, 0)
        ELSE COALESCE(settlements.paid_amount, 0)
    END,
    fm.pay_date = NULL,
    fm.status = CASE
        WHEN COALESCE(settlements.paid_amount, 0) > 0
             AND COALESCE(settlements.paid_amount, 0) = ABS(fm.amount) THEN 'PAID_OUT'
        WHEN fm.due_date < CURRENT_DATE() THEN 'LATE'
        ELSE 'PENDING'
    END,
    fm.last_modified_date = CURRENT_TIMESTAMP;
