-- Competence is mandatory for every movement. V8 deferred this constraint until the recurring
-- movements without occurrence_date were reconciled. This migration does not backfill those rows:
-- if any competence_date is still NULL, the ALTER fails and the database is left unchanged.
-- Services always set competence (automatic or informed), so new writes are not affected.
ALTER TABLE financial_movement
    MODIFY COLUMN competence_date DATE NOT NULL COMMENT 'Budget competence date of the movement';
