INSERT INTO financialbudgetdb.financial_movement
(id, code, operation_type, description, amount, amount_paid, movement_date, due_date, pay_date, status, flag_active, created_date, last_modified_date)
VALUES(1, UNHEX(REPLACE(UUID(), '-', '')), 'CREDIT', 'TESTE CRÉDITO', 10000, 10000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'PAID_OUT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);