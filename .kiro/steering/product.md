---
inclusion: always
---

# Personal Budget — Produto

## Propósito

O Personal Budget é uma API REST para **gerenciamento de orçamento pessoal**. O sistema permite que usuários controlem suas finanças registrando receitas, despesas, contas fixas recorrentes e compras parceladas, com visão de saldo diário projetado.

## Usuários-Alvo

Pessoas físicas que desejam controlar suas finanças pessoais via API (consumida por um frontend ou cliente mobile).

## Funcionalidades Principais

- **Movimentações financeiras**: registro avulso de receitas (`CREDIT`) e despesas (`DEBIT`)
- **Contas fixas**: lançamentos recorrentes com periodicidade semanal, mensal ou anual
- **Contas parceladas**: compras divididas em N parcelas com controle automático de lançamento
- **Saldo diário**: cálculo de saldo real (pagos) e projetado (pendentes + atrasados)
- **Dashboard**: consolidação de receitas, despesas e saldo do período

## Módulos de Domínio

### Financial Movement
Entidade central do sistema. Todas as outras fontes (contas fixas e parceladas) geram `FinancialMovement` ao serem processadas.
- Status: `PENDING`, `LATE`, `PAID_OUT`

### Fixed Bill
Conta recorrente com periodicidade configurável (`WEEKLY`, `MONTHLY`, `YEARLY`).
- Recorrência mensal com dias específicos via tabela `calendar_fixed_bill`
- Campo `next_due_date` controla a próxima data de lançamento
- Status: `ACTIVE`, `INACTIVE`

### Installment Bill
Conta parcelada (ex: compra no cartão).
- Controla `installment_total` e `installment_count`
- Encerra automaticamente quando todas as parcelas são lançadas

### Balance
Agrega `FinancialMovement`, `FixedBill` e `InstallmentBill` para calcular saldo real e projetado por dia.

### Dashboard
Visão gerencial consolidada de receitas, despesas e saldo do período.

## Job Agendado

`RecurrenceBillJob` executa diariamente à meia-noite (`0 0 0 * * *`):
1. Busca `FixedBill` com `next_due_date` = hoje
2. Busca `InstallmentBill` com `next_installment_date` = hoje
3. Gera `FinancialMovement` com status `PENDING` para cada um
4. Avança `next_due_date` / `next_installment_date` para o próximo ciclo
