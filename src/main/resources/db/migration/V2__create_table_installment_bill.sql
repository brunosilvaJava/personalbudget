create table if not exists installment_bill (
    id                      BIGINT          PRIMARY KEY AUTO_INCREMENT          COMMENT 'Primary key',
    code                    BINARY(16)      NOT NULL                            COMMENT 'Unique identifier of the installment account',
    description             VARCHAR(50)     NOT NULL                            COMMENT 'Description of the installment account',
    amount                  DECIMAL(10,2)   NOT NULL                            COMMENT 'Amount of the installment account',
    operation_type      VARCHAR(20)     NOT NULL                            COMMENT 'Type of installment account entry',
    status                  VARCHAR(20)                                         COMMENT 'Status of the installment account',
    bill_date               DATETIME                                            COMMENT 'Date of purchase of the installment account',
    installment_count       SMALLINT                                            COMMENT 'Number of installments of the installment account',
    next_installment_date   DATETIME                                            COMMENT 'Date of the next installment of the installment account',
    flag_active             TINYINT(1)      NOT NULL                            COMMENT 'Logical deletion flag',
    created_date            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT 'Date of creation of the transaction',
    last_modified_date      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT 'Date of the last change of the transaction'
) COMMENT 'installment accounts table';
