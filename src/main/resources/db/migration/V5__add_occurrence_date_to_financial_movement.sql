ALTER TABLE financial_movement
    ADD COLUMN occurrence_date DATE NULL COMMENT 'Scheduled occurrence date of the recurrence bill';

UPDATE financial_movement
SET occurrence_date = DATE(due_date)
WHERE recurrence_bill_code IS NOT NULL;

-- Duplicatas já existentes: mantém o menor id e libera as demais (NULL não conflita com o índice único).
UPDATE financial_movement fm
JOIN (
    SELECT recurrence_bill_code, occurrence_date, MIN(id) AS keep_id
    FROM financial_movement
    WHERE occurrence_date IS NOT NULL
    GROUP BY recurrence_bill_code, occurrence_date
    HAVING COUNT(*) > 1
) dup ON dup.recurrence_bill_code = fm.recurrence_bill_code
     AND dup.occurrence_date = fm.occurrence_date
SET fm.occurrence_date = NULL
WHERE fm.id <> dup.keep_id;

ALTER TABLE financial_movement
    ADD CONSTRAINT uk_financial_movement_recurrence_occurrence
    UNIQUE (recurrence_bill_code, occurrence_date);
