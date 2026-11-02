-- Competence: when the obligation/revenue belongs to the budget, independent of due/pay dates.
-- Expansive migration: add (nullable) -> backfill -> diagnose. NOT NULL on competence_date is
-- deliberately postponed to a later migration, after the recurring movements without
-- occurrence_date (see below) are reconciled.
ALTER TABLE financial_movement
    ADD COLUMN competence_date DATE NULL COMMENT 'Budget competence date of the movement',
    ADD COLUMN competence_overridden TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'True when the competence was informed by the client and must not be recalculated';

-- Standalone movements: competence is the movement date.
UPDATE financial_movement
SET competence_date = DATE(movement_date)
WHERE recurrence_bill_code IS NULL;

-- Recurring movements with an occurrence: competence is the scheduled occurrence date.
UPDATE financial_movement
SET competence_date = occurrence_date
WHERE recurrence_bill_code IS NOT NULL
  AND occurrence_date IS NOT NULL;

-- Recurring movements WITHOUT occurrence_date (V5 released it on duplicates) are intentionally
-- left with competence_date NULL: the current due_date/movement_date may have been edited, so it
-- is not known to be the original occurrence. occurrence_date (the idempotency key) is not touched.
-- Diagnostics to run before reconciling them and before making competence_date NOT NULL:
--
--   SELECT id, code, recurrence_bill_code, movement_date, due_date, status
--   FROM financial_movement
--   WHERE recurrence_bill_code IS NOT NULL AND occurrence_date IS NULL;
--
--   SELECT id, code, operation_type, amount, amount_paid, status
--   FROM financial_movement
--   WHERE status = 'PAID_OUT' AND ABS(amount) <> ABS(amount_paid);   -- not reinterpreted

CREATE INDEX idx_financial_movement_competence_date ON financial_movement (competence_date);
