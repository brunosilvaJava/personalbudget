-- Pay cycles (E4a): the configurable financial cycle opened by the anchor income schedule.
--
-- pay_cycle_config holds the single configuration row. It is created in CALENDAR_MONTH (the behaviour
-- the application had before cycles) and is also the serialization point: every write to the cycles
-- starts by locking this row, so two writers never build the cycle chain at the same time.
--
-- pay_cycle holds the cycles as [start_date, end_date): the end is exclusive, so the day a new cycle
-- opens belongs to the new cycle only. A cycle is identified by its code; its dates may move while the
-- boundary is not frozen, its code never does. Only ACTIVE or CANCELLED is stored: whether a cycle is
-- planned, open or waiting to be closed is derived from its dates and the clock when it is read.
CREATE TABLE IF NOT EXISTS pay_cycle_config
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)  NOT NULL COMMENT 'Pay cycle configuration uuid code',
    mode               VARCHAR(20) NOT NULL COMMENT 'CALENDAR_MONTH or PAY_CYCLE',
    anchor_schedule_id BIGINT      NULL COMMENT 'Anchor income schedule in use; null in CALENDAR_MONTH',
    activation_date    DATE        NULL COMMENT 'Start of the first cycle of the current activation; null in CALENDAR_MONTH',
    version            BIGINT      NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_date       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_pay_cycle_config_code UNIQUE (code),
    CONSTRAINT fk_pay_cycle_config_anchor_schedule FOREIGN KEY (anchor_schedule_id) REFERENCES income_schedule (id),
    CONSTRAINT ck_pay_cycle_config_mode CHECK (mode IN ('CALENDAR_MONTH', 'PAY_CYCLE')),
    CONSTRAINT ck_pay_cycle_config_pay_cycle_has_anchor CHECK (
        mode = 'CALENDAR_MONTH' OR (anchor_schedule_id IS NOT NULL AND activation_date IS NOT NULL))
) COMMENT 'Pay cycle configuration (single row)';

INSERT INTO pay_cycle_config (code, mode)
SELECT UUID_TO_BIN(UUID()), 'CALENDAR_MONTH'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pay_cycle_config);

CREATE TABLE IF NOT EXISTS pay_cycle
(
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code                 BINARY(16)  NOT NULL COMMENT 'Pay cycle uuid code, the public identifier',
    start_date           DATE        NOT NULL COMMENT 'First day of the cycle, inclusive',
    end_date             DATE        NOT NULL COMMENT 'Day the next cycle opens, exclusive',
    kind                 VARCHAR(20) NOT NULL COMMENT 'REGULAR or TRANSITION',
    anchor_schedule_id   BIGINT      NULL COMMENT 'Schedule of the anchor occurrence that opens the cycle; null for a TRANSITION without one',
    anchor_nominal_month DATE        NULL COMMENT 'Nominal month (1st day) of the anchor occurrence that opens the cycle',
    lifecycle            VARCHAR(20) NOT NULL COMMENT 'ACTIVE or CANCELLED',
    start_adjusted       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT 'Start fixed by a manual adjustment, not recalculated',
    end_adjusted         TINYINT(1)  NOT NULL DEFAULT 0 COMMENT 'End fixed by a manual adjustment, not recalculated',
    version              BIGINT      NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    -- The generated columns are NULL unless the cycle is ACTIVE, and NULLs do not collide in a UNIQUE
    -- key: a cancelled cycle gives its anchor occurrence and its dates back.
    active_anchor_schedule_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN lifecycle = 'ACTIVE' THEN anchor_schedule_id END) STORED,
    active_anchor_nominal_month DATE GENERATED ALWAYS AS (
        CASE WHEN lifecycle = 'ACTIVE' THEN anchor_nominal_month END) STORED,
    active_start_date    DATE GENERATED ALWAYS AS (CASE WHEN lifecycle = 'ACTIVE' THEN start_date END) STORED,
    active_end_date      DATE GENERATED ALWAYS AS (CASE WHEN lifecycle = 'ACTIVE' THEN end_date END) STORED,
    created_date         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    last_modified_date   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last modification date',
    CONSTRAINT uk_pay_cycle_code UNIQUE (code),
    CONSTRAINT uk_pay_cycle_active_anchor_occurrence UNIQUE (active_anchor_schedule_id, active_anchor_nominal_month),
    CONSTRAINT uk_pay_cycle_active_start_date UNIQUE (active_start_date),
    CONSTRAINT uk_pay_cycle_active_end_date UNIQUE (active_end_date),
    CONSTRAINT fk_pay_cycle_anchor_schedule FOREIGN KEY (anchor_schedule_id) REFERENCES income_schedule (id),
    CONSTRAINT ck_pay_cycle_dates CHECK (start_date < end_date),
    CONSTRAINT ck_pay_cycle_kind CHECK (kind IN ('REGULAR', 'TRANSITION')),
    CONSTRAINT ck_pay_cycle_lifecycle CHECK (lifecycle IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_pay_cycle_anchor_pair CHECK (
        (anchor_schedule_id IS NULL AND anchor_nominal_month IS NULL)
        OR (anchor_schedule_id IS NOT NULL AND anchor_nominal_month IS NOT NULL))
) COMMENT 'Pay cycles, [start_date, end_date)';

CREATE INDEX idx_pay_cycle_lifecycle_start_date ON pay_cycle (lifecycle, start_date);
