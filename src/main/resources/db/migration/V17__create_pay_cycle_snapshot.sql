CREATE TABLE pay_cycle_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code BINARY(16) NOT NULL,
    pay_cycle_id BIGINT NOT NULL,
    snapshot_version INT NOT NULL,
    cycle_version BIGINT NOT NULL,
    recognized_revenue DECIMAL(19, 2) NOT NULL,
    recognized_expense DECIMAL(19, 2) NOT NULL,
    projected_revenue DECIMAL(19, 2) NOT NULL,
    projected_expense DECIMAL(19, 2) NOT NULL,
    created_date DATETIME(6) NOT NULL,
    CONSTRAINT uk_pay_cycle_snapshot_code UNIQUE (code),
    CONSTRAINT uk_pay_cycle_snapshot_version UNIQUE (pay_cycle_id, snapshot_version),
    CONSTRAINT uk_pay_cycle_snapshot_cycle_version UNIQUE (pay_cycle_id, cycle_version),
    CONSTRAINT fk_pay_cycle_snapshot_cycle FOREIGN KEY (pay_cycle_id) REFERENCES pay_cycle (id),
    CONSTRAINT ck_pay_cycle_snapshot_version CHECK (snapshot_version > 0)
);

CREATE TABLE pay_cycle_snapshot_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    snapshot_id BIGINT NOT NULL,
    category_key VARCHAR(36) NOT NULL,
    category_code BINARY(16) NULL,
    category_name VARCHAR(50) NOT NULL,
    operation_type VARCHAR(10) NOT NULL,
    recognized_amount DECIMAL(19, 2) NOT NULL,
    projected_amount DECIMAL(19, 2) NOT NULL,
    CONSTRAINT uk_pay_cycle_snapshot_category UNIQUE (snapshot_id, operation_type, category_key),
    CONSTRAINT fk_pay_cycle_snapshot_category_snapshot FOREIGN KEY (snapshot_id) REFERENCES pay_cycle_snapshot (id),
    CONSTRAINT ck_pay_cycle_snapshot_category_type CHECK (operation_type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_pay_cycle_snapshot_category_key CHECK (
        (category_code IS NULL AND category_key = 'UNCATEGORIZED')
        OR (category_code IS NOT NULL AND category_key <> 'UNCATEGORIZED'))
);
