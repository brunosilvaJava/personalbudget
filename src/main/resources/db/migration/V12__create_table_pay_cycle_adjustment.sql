-- Pay cycle adjustments (E4b): the history of what moved the boundaries of the cycles or turned them off.
--
-- Insert only: a row is never updated or deleted, and no code path does either (the entity is
-- immutable). Undoing an adjustment is another adjustment, with its own preview and justification.
--
-- previous_cycle_id and next_cycle_id are the cycles the change touched:
--   MANUAL_BOUNDARY  the pair around the boundary that moved (old_boundary -> new_boundary);
--   DEACTIVATION     the last cycle that had started (kept as history) and the first one that was
--                    cancelled; old_boundary is the start of that first cancelled cycle. Either side
--                    may be absent: nothing had started yet, or nothing was left to cancel.
-- expected_previous_version and expected_next_version are the versions of the two cycles the caller
-- said it had read; expected_config_version is the configuration's, because a deactivation is a write
-- to the configuration, not to a cycle.
--
-- ANCHOR_TRANSITION and ACTIVATION are accepted here so the steps that record them do not have to
-- change the constraint.
CREATE TABLE IF NOT EXISTS pay_cycle_adjustment
(
    id                        BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code                      BINARY(16)   NOT NULL COMMENT 'Pay cycle adjustment uuid code',
    type                      VARCHAR(30)  NOT NULL COMMENT 'MANUAL_BOUNDARY, ANCHOR_TRANSITION, ACTIVATION or DEACTIVATION',
    previous_cycle_id         BIGINT       NULL COMMENT 'Cycle before the boundary the change touched',
    next_cycle_id             BIGINT       NULL COMMENT 'Cycle after the boundary the change touched',
    old_boundary              DATE         NULL COMMENT 'Boundary before the change: the day the next cycle opened',
    new_boundary              DATE         NULL COMMENT 'Boundary after the change',
    justification             VARCHAR(500) NULL COMMENT 'Why the change was made; required for MANUAL_BOUNDARY and DEACTIVATION',
    expected_previous_version BIGINT       NULL COMMENT 'Version of the previous cycle the caller expected',
    expected_next_version     BIGINT       NULL COMMENT 'Version of the next cycle the caller expected',
    expected_config_version   BIGINT       NULL COMMENT 'Version of the configuration the caller expected',
    created_date              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation date',
    CONSTRAINT uk_pay_cycle_adjustment_code UNIQUE (code),
    CONSTRAINT fk_pay_cycle_adjustment_previous_cycle FOREIGN KEY (previous_cycle_id) REFERENCES pay_cycle (id),
    CONSTRAINT fk_pay_cycle_adjustment_next_cycle FOREIGN KEY (next_cycle_id) REFERENCES pay_cycle (id),
    CONSTRAINT ck_pay_cycle_adjustment_type CHECK (
        type IN ('MANUAL_BOUNDARY', 'ANCHOR_TRANSITION', 'ACTIVATION', 'DEACTIVATION')),
    CONSTRAINT ck_pay_cycle_adjustment_justification CHECK (
        type NOT IN ('MANUAL_BOUNDARY', 'DEACTIVATION')
        OR (justification IS NOT NULL AND CHAR_LENGTH(TRIM(justification)) > 0)),
    CONSTRAINT ck_pay_cycle_adjustment_manual_boundary CHECK (
        type <> 'MANUAL_BOUNDARY'
        OR (previous_cycle_id IS NOT NULL AND next_cycle_id IS NOT NULL
            AND old_boundary IS NOT NULL AND new_boundary IS NOT NULL AND old_boundary <> new_boundary))
) COMMENT 'Pay cycle adjustments history (insert only)';

CREATE INDEX idx_pay_cycle_adjustment_previous_cycle ON pay_cycle_adjustment (previous_cycle_id, created_date);
CREATE INDEX idx_pay_cycle_adjustment_next_cycle ON pay_cycle_adjustment (next_cycle_id, created_date);
