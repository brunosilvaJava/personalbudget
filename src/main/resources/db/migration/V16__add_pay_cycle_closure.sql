-- E6 foundation: closed cycles keep their place in the chain; only cancelled cycles release dates.
ALTER TABLE pay_cycle
    DROP INDEX uk_pay_cycle_active_anchor_occurrence,
    DROP INDEX uk_pay_cycle_active_start_date,
    DROP INDEX uk_pay_cycle_active_end_date,
    DROP COLUMN active_anchor_schedule_id,
    DROP COLUMN active_anchor_nominal_month,
    DROP COLUMN active_start_date,
    DROP COLUMN active_end_date,
    DROP CHECK ck_pay_cycle_lifecycle;

ALTER TABLE pay_cycle
    ADD COLUMN active_anchor_schedule_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN lifecycle IN ('ACTIVE', 'CLOSED') THEN anchor_schedule_id END) STORED,
    ADD COLUMN active_anchor_nominal_month DATE GENERATED ALWAYS AS (
        CASE WHEN lifecycle IN ('ACTIVE', 'CLOSED') THEN anchor_nominal_month END) STORED,
    ADD COLUMN active_start_date DATE GENERATED ALWAYS AS (
        CASE WHEN lifecycle IN ('ACTIVE', 'CLOSED') THEN start_date END) STORED,
    ADD COLUMN active_end_date DATE GENERATED ALWAYS AS (
        CASE WHEN lifecycle IN ('ACTIVE', 'CLOSED') THEN end_date END) STORED,
    ADD CONSTRAINT uk_pay_cycle_active_anchor_occurrence
        UNIQUE (active_anchor_schedule_id, active_anchor_nominal_month),
    ADD CONSTRAINT uk_pay_cycle_active_start_date UNIQUE (active_start_date),
    ADD CONSTRAINT uk_pay_cycle_active_end_date UNIQUE (active_end_date),
    ADD CONSTRAINT ck_pay_cycle_lifecycle CHECK (lifecycle IN ('ACTIVE', 'CANCELLED', 'CLOSED'));
