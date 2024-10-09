create table if not exists financial_movement(
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT          COMMENT 'Primary key',
    code                BINARY(16)      NOT NULL                            COMMENT 'Financial transaction uuid code',
    operation_type      VARCHAR(20)     NOT NULL                            COMMENT 'Type of transaction in the financial transaction (CREDIT; DEBIT)',
    description         VARCHAR(50)     NOT NULL                            COMMENT 'Financial transaction description',
    amount              DECIMAL(10,2)   NOT NULL                            COMMENT 'Financial transaction amount',
    amount_paid         DECIMAL(10,2)                                       COMMENT 'Financial transaction payment amount',
    movement_date       DATETIME        NOT NULL                            COMMENT 'Financial transaction date',
    due_date            DATETIME        NOT NULL                            COMMENT 'Financial transaction due date',
    pay_date            DATETIME                                            COMMENT 'Financial transaction payment date',
    status              VARCHAR(20)     NOT NULL                            COMMENT 'Financial transaction status',
    flag_active         TINYINT(1)      NOT NULL                            COMMENT 'Logical exclusion flag',
    created_date        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT 'Transaction creation date',
    last_modified_date  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT 'Transaction last modified date'
) COMMENT 'financial transactions table'