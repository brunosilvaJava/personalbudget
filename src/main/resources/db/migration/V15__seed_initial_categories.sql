INSERT INTO category (code, name, operation_type, status, category_group_id)
SELECT UUID_TO_BIN(UUID()), seed.name, seed.operation_type, 'ACTIVE', category_group.id
FROM (
    SELECT 'Aluguel/Financiamento' AS name, 'DEBIT' AS operation_type, 'Residência' AS group_name
    UNION ALL SELECT 'Água', 'DEBIT', 'Residência'
    UNION ALL SELECT 'Energia elétrica', 'DEBIT', 'Residência'
    UNION ALL SELECT 'Gás', 'DEBIT', 'Residência'
    UNION ALL SELECT 'Internet', 'DEBIT', 'Residência'
    UNION ALL SELECT 'Manutenção da casa', 'DEBIT', 'Residência'
    UNION ALL SELECT 'Alimentação', 'DEBIT', 'Pessoal'
    UNION ALL SELECT 'Saúde', 'DEBIT', 'Pessoal'
    UNION ALL SELECT 'Vestuário', 'DEBIT', 'Pessoal'
    UNION ALL SELECT 'Lazer', 'DEBIT', 'Pessoal'
    UNION ALL SELECT 'Cuidados pessoais', 'DEBIT', 'Pessoal'
    UNION ALL SELECT 'Educação', 'DEBIT', 'Instrução'
    UNION ALL SELECT 'Cursos', 'DEBIT', 'Instrução'
    UNION ALL SELECT 'Livros e materiais', 'DEBIT', 'Instrução'
    UNION ALL SELECT 'Combustível', 'DEBIT', 'Moto'
    UNION ALL SELECT 'Manutenção', 'DEBIT', 'Moto'
    UNION ALL SELECT 'Seguro', 'DEBIT', 'Moto'
    UNION ALL SELECT 'Documentação', 'DEBIT', 'Moto'
    UNION ALL SELECT 'Reserva de emergência', 'DEBIT', 'Investimento'
    UNION ALL SELECT 'Investimentos', 'DEBIT', 'Investimento'
    UNION ALL SELECT 'Impostos e taxas', 'DEBIT', 'Outros'
    UNION ALL SELECT 'Doações', 'DEBIT', 'Outros'
    UNION ALL SELECT 'Outros', 'DEBIT', 'Outros'
    UNION ALL SELECT 'Salário', 'CREDIT', 'Receitas'
    UNION ALL SELECT 'Renda extra', 'CREDIT', 'Receitas'
    UNION ALL SELECT 'Rendimentos', 'CREDIT', 'Receitas'
    UNION ALL SELECT 'Reembolso', 'CREDIT', 'Receitas'
    UNION ALL SELECT 'Outros', 'CREDIT', 'Receitas'
) AS seed
JOIN category_group ON category_group.name = seed.group_name
WHERE NOT EXISTS (
    SELECT 1
    FROM category
    WHERE category.name = seed.name
      AND category.operation_type = seed.operation_type
);
