create table if not exists financial_movement(
    id                  INTEGER         PRIMARY KEY AUTO_INCREMENT              COMMENT 'chave primária',
    code                BINARY(16)      NOT NULL                                COMMENT 'código uuid da movimentação financeira',
    operation_type      VARCHAR(20)     NOT NULL                                COMMENT 'tipo da operação na movimentação financeira (CREDIT; DEBIT)',
    description         VARCHAR(50)     NOT NULL                                COMMENT 'descrição da movimentação financeira',
    amount              DECIMAL(10,2)   NOT NULL                                COMMENT 'valor da movimentação financeira',
    amount_paid         DECIMAL(10,2)                                           COMMENT 'valor do pagamento da movimentação financeira',
    movement_date       DATETIME        NOT NULL                                COMMENT 'data da movimentação financeira',
    due_date            DATETIME        NOT NULL                                COMMENT 'data do vencimento da movimentação financeira',
    pay_date            DATETIME                                                COMMENT 'data do pagamento da movimentação financeira',
    status              VARCHAR(20)     NOT NULL                                COMMENT 'situação da movimentação financeira',
    flag_active         TINYINT(1)      NOT NULL                                COMMENT 'flag de exclusão lógica',
    created_date        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT 'data de criação da movimentação',
    last_modified_date  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT 'data da última alteração da movimentação'
) COMMENT 'tabela de movimentações financeiras'