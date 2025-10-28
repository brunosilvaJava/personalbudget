CREATE TABLE IF NOT EXISTS fixed_bill
(
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key',
    code               BINARY(16)     NOT NULL COMMENT 'Unique identifier of the fixed account',
    description        VARCHAR(50)    NOT NULL COMMENT 'Description of the fixed account',
    amount             DECIMAL(10, 2) NOT NULL COMMENT 'Amount of the fixed account',
    operation_type     VARCHAR(20)    NOT NULL COMMENT 'Transaction type of the fixed account',
    recurrence_type    VARCHAR(50) COMMENT 'Recurrence type of the fixed account',
    status             VARCHAR(20) COMMENT 'Status of the fixed account',
    start_date         DATE COMMENT 'Start date of the fixed account',
    end_date           DATE COMMENT 'End date of the fixed account',
    next_due_date      DATE COMMENT 'Next due date',
    reference_year     INTEGER COMMENT 'Ano de referência para recorrência anual (YEARLY)',
    flag_active        TINYINT(1)     NOT NULL COMMENT 'Logical deletion flag',
    created_date       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Date of creation of the transaction',
    last_modified_date DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Date of last modification of the transaction'
) COMMENT 'Fixed bill table';
