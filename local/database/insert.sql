INSERT IGNORE INTO
	financialbudgetdb.financial_movement
	(id, code, operation_type, description,
	amount, amount_paid, movement_date, due_date,
	pay_date, status, flag_active, created_date, last_modified_date)
VALUES
	(1, UNHEX(REPLACE(UUID(), '-', '')), 'CREDIT', 'TESTE CRÉDITO',
	10000, 10000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
	'PAID_OUT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO
	financialbudgetdb.financial_movement
	(id, code, operation_type, description,
	amount, amount_paid, movement_date, due_date,
	pay_date, status, flag_active, created_date, last_modified_date)
VALUES
	(2, UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'TESTE DÉBITO',
	7874, 135, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
	'PENDING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO
	financialbudgetdb.financial_movement
	(id, code, operation_type, description,
	amount, amount_paid, movement_date, due_date,
	pay_date, status, flag_active, created_date, last_modified_date)
VALUES
	(3, UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'TESTE DÉBITO',
	11478, 378, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
	'PENDING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO
	financialbudgetdb.financial_movement
	(id, code, operation_type, description,
	amount, amount_paid, movement_date, due_date,
	pay_date, status, flag_active, created_date, last_modified_date)
VALUES
	(4, UNHEX(REPLACE(UUID(), '-', '')), 'CREDIT', 'TESTE CRÉDITO',
	5829, 250, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
	'LATE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO
	financialbudgetdb.financial_movement
	(id, code, operation_type, description,
	amount, amount_paid, movement_date, due_date,
	pay_date, status, flag_active, created_date, last_modified_date)
VALUES
	(5, UNHEX(REPLACE(UUID(), '-', '')), 'DEBIT', 'TESTE DÉBITO',
	15750, 15750, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
	'PAID_OUT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO fixed_bill
    (code, flag_active, operation_type, description, amount, status, start_date, next_due_date, end_date, recurrence_type)
VALUES

    (UNHEX(REPLACE(UUID(), '-', '')), TRUE, 'DEBIT', 'Weekly Expense', 500.00, 'ACTIVE', '2024-01-01', '2024-01-08', '2024-12-31', 'WEEKLY'),
    (UNHEX(REPLACE(UUID(), '-', '')), TRUE, 'CREDIT', 'Monthly Income', 3000.00, 'ACTIVE', '2024-01-01', '2024-02-01', '2024-12-31', 'MONTHLY'),
    (UNHEX(REPLACE(UUID(), '-', '')), FALSE, 'DEBIT', 'Annual Payment', 12000.00, 'INACTIVE', '2023-01-01', '2024-01-01', '2023-12-31', 'YEARLY'),
    (UNHEX(REPLACE(UUID(), '-', '')), TRUE, 'DEBIT', 'Leap Year Weekly Expense', 700.00, 'ACTIVE', '2024-01-01', '2024-01-15', '2024-12-31', 'WEEKLY'),
    (UNHEX(REPLACE(UUID(), '-', '')), FALSE, 'CREDIT', 'Inactive Monthly Income', 4000.00, 'INACTIVE', '2023-01-01', '2023-01-31', '2023-12-31', 'MONTHLY');

INSERT IGNORE INTO installment_bill
    (code, flag_active, operation_type, description, amount, status, purchase_date, installment_total, installment_count, last_installment_date, next_installment_date)
VALUES
    (UNHEX(REPLACE(UUID(), '-', '')), TRUE, 'DEBIT', 'Compra de electrodomésticos', 1200.00, 'PENDING', '2024-01-01', 12, 3, '2024-03-01', '2024-04-01'),
    (UNHEX(REPLACE(UUID(), '-', '')), FALSE, 'DEBIT', 'Pago de curso online', 600.00, 'DONE', '2023-06-01', 6, 6, '2023-12-01', '2023-12-01'),
    (UNHEX(REPLACE(UUID(), '-', '')), TRUE, 'CREDIT', 'Préstamo personal', 5000.00, 'PENDING', '2024-02-01', 10, 1, '2024-02-01', '2024-03-01'),
    (UNHEX(REPLACE(UUID(), '-', '')), TRUE, 'DEBIT', 'Plan de pago de coche', 24000.00, 'PENDING', '2024-01-15', 24, 5, '2024-05-15', '2024-06-15'),
    (UNHEX(REPLACE(UUID(), '-', '')), FALSE, 'DEBIT', 'Compra de computadora', 3000.00, 'DONE', '2023-09-01', 3, 3, '2023-11-01', '2023-11-01');

INSERT IGNORE INTO calendar_fixed_bill
    (day_launch, flg_leap_year, flg_active, id_fixed_bill, created_date, last_modified_date)
VALUES
    (1, FALSE, TRUE, 1, NOW(), NOW()),
    (7, FALSE, TRUE, 2, NOW(), NOW()),
    (1, FALSE, FALSE, 3, NOW(), NOW()),
    (7, TRUE, TRUE, 4, NOW(), NOW()),
    (2, FALSE, FALSE, 5, NOW(), NOW());