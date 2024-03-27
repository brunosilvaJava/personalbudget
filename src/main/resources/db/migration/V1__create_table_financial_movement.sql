create table if not exists FINANCIAL_MOVEMENT(
    id INTEGER PRIMARY KEY AUTO_INCREMENT COMMENT 'chave primária',
    operation_type VARCHAR(20) NOT NULL COMMENT 'tipo da operação financeira (CREDIT; DEBIT)',
    description VARCHAR(50) NOT NULL COMMENT 'descrição da movimentação financeira',
    amount DECIMAL(4, 2) NOT NULL COMMENT 'valor da movimentação financeira',
    amount_paid DECIMAL(4,2) COMMENT 'valor do pagamento',
    movement_date DATETIME NOT NULL COMMENT 'data da movimentação financeira',
    due_date DATETIME NOT NULL COMMENT 'data do vencimento da movimentação financeira',
    pay_date DATETIME COMMENT 'data do pagamento da movimentação financeira',
    status VARCHAR(20) NOT NULL COMMENT 'situação da movimentação financeira',
    flag_active TINYINT(1) NOT NULL COMMENT 'flag de exclusão lógica'
)