-- Income schedules (E3): the main income as its own domain, not as a FixedBill rule.
--
-- income_schedule holds the rule (fixed day or Nth business day). income_occurrence exists only
-- once an occurrence was materialized as a FinancialMovement: future occurrences are computed on
-- demand from the rule and the holiday calendar, so a calendar change moves them without leaving
-- stale rows behind. The identity of an occurrence is (schedule, nominal month), never the date:
-- a holiday can shift the date, and must not create a second paycheck for the same month.
CREATE TABLE IF NOT EXISTS income_schedule
(
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code                    BINARY(16)     NOT NULL COMMENT 'Income schedule uuid code',
    description             VARCHAR(50)    NOT NULL COMMENT 'Description copied to the generated movements',
    amount                  DECIMAL(10, 2) NOT NULL COMMENT 'Expected amount of each occurrence (positive)',
    category_code           BINARY(16)     NULL COMMENT 'Category (CREDIT) copied to the generated movements',
    rule_type               VARCHAR(30)    NOT NULL COMMENT 'FIXED_DAY or NTH_BUSINESS_DAY',
    rule_value              INTEGER        NOT NULL COMMENT 'Day of the month (1-31) or business day position',
    business_week           VARCHAR(30)    NOT NULL COMMENT 'MONDAY_TO_FRIDAY or MONDAY_TO_SATURDAY',
    non_business_day_policy VARCHAR(20)    NULL COMMENT 'ANTICIPATE, POSTPONE or KEEP; only for FIXED_DAY',
    start_month             DATE           NOT NULL COMMENT 'First nominal month served, always the 1st day',
    end_month               DATE           NULL COMMENT 'Last nominal month served, always the 1st day',
    is_anchor               TINYINT(1)     NOT NULL COMMENT 'True for the main income that will open the pay cycles',
    status                  VARCHAR(20)    NOT NULL COMMENT 'ACTIVE or INACTIVE',
    replaces_fixed_bill_code BINARY(16)   NULL COMMENT 'FixedBill this schedule took over the receipt from',
    -- 1 only for the single ACTIVE anchor; NULL otherwise, and NULLs do not collide in a UNIQUE key.
    active_anchor_slot      TINYINT GENERATED ALWAYS AS (CASE WHEN is_anchor = 1 AND status = 'ACTIVE' THEN 1 END) STORED,
    active_replacement_fixed_bill_code BINARY(16) GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN replaces_fixed_bill_code END
    ) STORED,
    created_date            DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_income_schedule_code UNIQUE (code),
    CONSTRAINT uk_income_schedule_single_active_anchor UNIQUE (active_anchor_slot),
    CONSTRAINT uk_income_schedule_active_fixed_bill_replacement UNIQUE (active_replacement_fixed_bill_code)
) COMMENT 'Income schedule table';

CREATE TABLE IF NOT EXISTS income_occurrence
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)     NOT NULL COMMENT 'Income occurrence uuid code',
    income_schedule_id BIGINT         NOT NULL COMMENT 'Schedule that generated the occurrence',
    nominal_month      DATE           NOT NULL COMMENT 'Month the occurrence belongs to, always the 1st day',
    planned_date       DATE           NOT NULL COMMENT 'Date the occurrence was materialized on; frozen afterwards',
    planned_amount     DECIMAL(10, 2) NOT NULL COMMENT 'Amount at the moment of the materialization',
    movement_code      BINARY(16)     NOT NULL COMMENT 'Generated FinancialMovement; kept even if it is soft-deleted',
    created_date       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_income_occurrence_code UNIQUE (code),
    CONSTRAINT uk_income_occurrence_schedule_month UNIQUE (income_schedule_id, nominal_month),
    CONSTRAINT uk_income_occurrence_movement UNIQUE (movement_code),
    CONSTRAINT fk_income_occurrence_schedule FOREIGN KEY (income_schedule_id) REFERENCES income_schedule (id)
) COMMENT 'Materialized income occurrences';
