-- ANCHOR_TRANSITION history is created by the explicit transition API and must always explain the change.
ALTER TABLE pay_cycle_adjustment
    DROP CHECK ck_pay_cycle_adjustment_justification,
    ADD CONSTRAINT ck_pay_cycle_adjustment_justification CHECK (
        type NOT IN ('MANUAL_BOUNDARY', 'DEACTIVATION', 'ANCHOR_TRANSITION')
        OR (justification IS NOT NULL AND CHAR_LENGTH(TRIM(justification)) > 0)
    );
