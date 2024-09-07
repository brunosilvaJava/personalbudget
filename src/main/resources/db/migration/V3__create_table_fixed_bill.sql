create table if not exists fixed_bill (
    id                      INTEGER         PRIMARY KEY AUTO_INCREMENT          COMMENT 'Primary key',
    code                    BINARY(16)      NOT NULL                            COMMENT 'Unique identifier of the fixed account',
    description             VARCHAR(50)     NOT NULL                            COMMENT 'Description of the fixed account',
    amount                  DECIMAL(10,2)   NOT NULL                            COMMENT 'Amount of the fixed account',
    journal_entry_type      VARCHAR(20)     NOT NULL                            COMMENT 'Transaction type of the fixed account',
    status                  VARCHAR(20)                                         COMMENT 'Status of the fixed account',
    start_date              DATETIME                                            COMMENT 'Start date of the fixed account',
    end_date                DATETIME                                            COMMENT 'End date of the fixed account',
    recurrence_type         VARCHAR(50)                                         COMMENT 'Recurrence type of the fixed account',
    flag_active             TINYINT(1)      NOT NULL                            COMMENT 'Logical deletion flag',
    created_date            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT 'Date of creation of the transaction',
    last_modified_date      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT 'Date of last modification of the transaction'
) COMMENT 'fixed bill table';
