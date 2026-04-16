-- financial_movement
-- Regras:
--   PAID_OUT  → pay_date preenchido, amount_paid = amount
--   PENDING   → due_date >= hoje, pay_date NULL, amount_paid = 0
--   LATE      → due_date < hoje, pay_date NULL, amount_paid = 0
--   DEBIT     → amount e amount_paid armazenados como negativos (conforme setAmount/setAmountPaid da entidade)
--   CREDIT    → amount e amount_paid positivos

INSERT IGNORE INTO financialbudgetdb.financial_movement
    (id, code, operation_type, description,
     amount, amount_paid, movement_date, due_date,
     pay_date, status, flag_active, created_date, last_modified_date)
VALUES
    -- passado: pago
    (1, UNHEX(REPLACE(UUID(), '-', '')), 'CREDIT', 'Salário',
     500000, 500000,
     DATE_SUB(CURDATE(), INTERVAL 14 DAY), DATE_SUB(CURDATE(), INTERVAL 14 DAY),
     DATE_SUB(CURDATE(), INTERVAL 14 DAY),
     'PAID_OUT', 1, NOW(), NOW()),

    -- futuro: pendente
    (2, UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'Conta de luz',
     -15000, 0,
     CURDATE(), DATE_ADD(CURDATE(), INTERVAL 15 DAY),
     NULL, 'PENDING', 1, NOW(), NOW()),

    (3, UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'Conta de internet',
     -9900, 0,
     CURDATE(), DATE_ADD(CURDATE(), INTERVAL 10 DAY),
     NULL, 'PENDING', 1, NOW(), NOW()),

    -- passado: atrasado
    (4, UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'Aluguel mês anterior',
     -120000, 0,
     DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_SUB(CURDATE(), INTERVAL 15 DAY),
     NULL, 'LATE', 1, NOW(), NOW()),

    -- atrasados: vencidos antes de hoje
    (8, UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'Cartão de crédito',
     -85000, 0,
     DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY),
     NULL, 'LATE', 1, NOW(), NOW()),

    (9, UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'Plano de saúde',
     -42000, 0,
     DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_SUB(CURDATE(), INTERVAL 7 DAY),
     NULL, 'LATE', 1, NOW(), NOW()),

    (10, UNHEX(REPLACE(UUID(), '-', '')), 'CREDIT', 'Reembolso pendente',
     25000, 0,
     DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_SUB(CURDATE(), INTERVAL 2 DAY),
     NULL, 'LATE', 1, NOW(), NOW()),

    -- passado: pago
    (5, UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'Supermercado',
     -35000, -35000,
     DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_SUB(CURDATE(), INTERVAL 5 DAY),
     DATE_SUB(CURDATE(), INTERVAL 5 DAY),
     'PAID_OUT', 1, NOW(), NOW()),

    -- hoje: crédito pago
    (6, UNHEX(REPLACE(UUID(), '-', '')), 'CREDIT', 'Freelance recebido',
     80000, 80000,
     CURDATE(), CURDATE(), CURDATE(),
     'PAID_OUT', 1, NOW(), NOW()),

    -- hoje: débito pago
    (7, UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'Restaurante',
     -4500, -4500,
     CURDATE(), CURDATE(), CURDATE(),
     'PAID_OUT', 1, NOW(), NOW());

-- fixed_bill
-- Regras:
--   ACTIVE  → flag_active=TRUE, next_due_date >= hoje
--   INACTIVE → flag_active=FALSE, next_due_date NULL
--   MONTHLY com dias específicos → registros em calendar_fixed_bill

INSERT IGNORE INTO fixed_bill
    (code, flag_active, operation_type, description, amount, status, start_date, next_due_date, end_date, recurrence_type)
VALUES
    (UNHEX(REPLACE(UUID(), '-', '')), TRUE,  'DEBIT',  'Academia',
     19900, 'ACTIVE',
     DATE_SUB(CURDATE(), INTERVAL 90 DAY), DATE_ADD(CURDATE(), INTERVAL 6 DAY),  DATE_ADD(CURDATE(), INTERVAL 275 DAY), 'WEEKLY'),

    (UNHEX(REPLACE(UUID(), '-', '')), TRUE,  'CREDIT', 'Salário fixo',
     500000, 'ACTIVE',
     DATE_SUB(CURDATE(), INTERVAL 90 DAY), DATE_ADD(CURDATE(), INTERVAL 16 DAY), DATE_ADD(CURDATE(), INTERVAL 275 DAY), 'MONTHLY'),

    (UNHEX(REPLACE(UUID(), '-', '')), FALSE, 'DEBIT',  'Seguro anual',
     120000, 'INACTIVE',
     DATE_SUB(CURDATE(), INTERVAL 365 DAY), NULL, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'YEARLY'),

    (UNHEX(REPLACE(UUID(), '-', '')), TRUE,  'DEBIT',  'Transporte semanal',
     8000, 'ACTIVE',
     DATE_SUB(CURDATE(), INTERVAL 90 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY),  DATE_ADD(CURDATE(), INTERVAL 275 DAY), 'WEEKLY'),

    (UNHEX(REPLACE(UUID(), '-', '')), FALSE, 'CREDIT', 'Renda extra',
     80000, 'INACTIVE',
     DATE_SUB(CURDATE(), INTERVAL 365 DAY), NULL, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'MONTHLY');

-- calendar_fixed_bill
-- Apenas para fixed_bill MONTHLY (registros 2 e 5 acima)

INSERT IGNORE INTO calendar_fixed_bill
    (day_launch, id_fixed_bill, created_date, last_modified_date)
VALUES
    (1, 2, NOW(), NOW()),
    (5, 5, NOW(), NOW());

-- installment_bill
-- Regras:
--   PENDING → flag_active=TRUE, installment_count < installment_total, next_installment_date >= hoje
--   DONE    → flag_active=FALSE, installment_count = installment_total, next_installment_date = last_installment_date

INSERT IGNORE INTO installment_bill
    (code, flag_active, operation_type, description, amount, status,
     purchase_date, installment_total, installment_count, last_installment_date, next_installment_date)
VALUES
    (UNHEX(REPLACE(UUID(), '-', '')), TRUE,  'DEBIT',  'Notebook parcelado',
     600000, 'PENDING',
     DATE_SUB(CURDATE(), INTERVAL 60 DAY), 12, 2,
     DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_ADD(CURDATE(), INTERVAL 5 DAY)),

    (UNHEX(REPLACE(UUID(), '-', '')), FALSE, 'DEBIT',  'Curso online',
     60000, 'DONE',
     DATE_SUB(CURDATE(), INTERVAL 210 DAY), 6, 6,
     DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_SUB(CURDATE(), INTERVAL 30 DAY)),

    (UNHEX(REPLACE(UUID(), '-', '')), TRUE,  'CREDIT', 'Recebimento parcelado',
     200000, 'PENDING',
     DATE_SUB(CURDATE(), INTERVAL 45 DAY), 10, 1,
     DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY)),

    (UNHEX(REPLACE(UUID(), '-', '')), TRUE,  'DEBIT',  'Carro financiado',
     2400000, 'PENDING',
     DATE_SUB(CURDATE(), INTERVAL 90 DAY), 24, 3,
     DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_ADD(CURDATE(), INTERVAL 1 DAY)),

    (UNHEX(REPLACE(UUID(), '-', '')), FALSE, 'DEBIT',  'Geladeira parcelada',
     150000, 'DONE',
     DATE_SUB(CURDATE(), INTERVAL 120 DAY), 3, 3,
     DATE_SUB(CURDATE(), INTERVAL 60 DAY), DATE_SUB(CURDATE(), INTERVAL 60 DAY));
