-- Diagnóstico do E1 (competência). SOMENTE LEITURA: nenhuma consulta altera dados.
-- Rode antes de reconciliar as recorrentes sem ocorrência e antes de tornar competence_date NOT NULL.
-- Uso: mysql -h <host> -u <usuario> -p <banco> < local/database/diagnostics/e1-competence.sql
-- Cada consulta tem um título em uma linha de comentário, na ordem em que aparece no resultado.

-- 1) Resumo: recorrentes sem occurrence_date (lacuna da V5/V8) e o que bloqueia o NOT NULL.
SELECT 'recorrente sem occurrence_date' AS item, COUNT(*) AS total,
       SUM(flag_active = 1) AS ativas
FROM financial_movement
WHERE recurrence_bill_code IS NOT NULL AND occurrence_date IS NULL
UNION ALL
SELECT 'competence_date nula (todas)', COUNT(*), SUM(flag_active = 1)
FROM financial_movement
WHERE competence_date IS NULL
UNION ALL
SELECT 'competence_date nula em avulso (bloqueia o NOT NULL)', COUNT(*), SUM(flag_active = 1)
FROM financial_movement
WHERE competence_date IS NULL AND recurrence_bill_code IS NULL;

-- 2) Cada recorrente sem occurrence_date, com a conta de origem e possíveis irmãs mantidas.
-- A V5 zerou occurrence_date só das duplicatas; a irmã mantida (MIN(id)) tem a mesma recorrência.
-- candidate_occurrence_date é sugestão: mesma conta, mesmo valor, mesma descrição e mesmo mês de vencimento.
-- Várias linhas para a mesma movimentação significam mais de uma candidata: revisar à mão.
SELECT n.id, n.code, n.recurrence_bill_code, n.description, n.operation_type, n.amount,
       n.movement_date, n.due_date, n.status, n.flag_active, n.competence_date,
       CASE
           WHEN EXISTS (SELECT 1 FROM fixed_bill b WHERE b.code = n.recurrence_bill_code) THEN 'FIXED_BILL'
           WHEN EXISTS (SELECT 1 FROM installment_bill b WHERE b.code = n.recurrence_bill_code) THEN 'INSTALLMENT_BILL'
           WHEN EXISTS (SELECT 1 FROM income_schedule b WHERE b.code = n.recurrence_bill_code) THEN 'INCOME_SCHEDULE'
           ELSE 'CONTA_INEXISTENTE'
       END AS bill_type,
       k.id AS candidate_id, k.occurrence_date AS candidate_occurrence_date
FROM financial_movement n
LEFT JOIN financial_movement k
       ON k.recurrence_bill_code = n.recurrence_bill_code
      AND k.occurrence_date IS NOT NULL
      AND k.amount = n.amount
      AND k.description = n.description
      AND DATE_FORMAT(k.due_date, '%Y-%m') = DATE_FORMAT(n.due_date, '%Y-%m')
WHERE n.recurrence_bill_code IS NOT NULL
  AND n.occurrence_date IS NULL
ORDER BY n.recurrence_bill_code, n.due_date, n.id;

-- 3) Recorrentes sem occurrence_date cuja conta não existe mais em nenhuma tabela de conta.
-- Sem conta não há como consultar a regra, então a competência só pode ser informada à mão.
SELECT n.id, n.code, n.recurrence_bill_code, n.description, n.due_date, n.status
FROM financial_movement n
WHERE n.recurrence_bill_code IS NOT NULL
  AND n.occurrence_date IS NULL
  AND NOT EXISTS (SELECT 1 FROM fixed_bill b WHERE b.code = n.recurrence_bill_code)
  AND NOT EXISTS (SELECT 1 FROM installment_bill b WHERE b.code = n.recurrence_bill_code)
  AND NOT EXISTS (SELECT 1 FROM income_schedule b WHERE b.code = n.recurrence_bill_code)
ORDER BY n.id;

-- 4) Valor pago guardado diferente da soma das liquidações ativas (a V8 apontava esse caso como
-- "não reinterpretar"; a partir da V18 o agregado vem das liquidações). Diferença maior que um centavo.
SELECT fm.id, fm.code, fm.description, fm.amount, fm.amount_paid,
       COALESCE(s.total, 0) AS settlements_total,
       COALESCE(fm.amount_paid, 0) - COALESCE(s.total, 0) AS difference
FROM financial_movement fm
LEFT JOIN (
    SELECT financial_movement_id, SUM(amount) AS total
    FROM movement_settlement
    WHERE reversed_at IS NULL
    GROUP BY financial_movement_id
) s ON s.financial_movement_id = fm.id
WHERE ABS(COALESCE(fm.amount_paid, 0) - COALESCE(s.total, 0)) > 0.005
ORDER BY fm.id;
