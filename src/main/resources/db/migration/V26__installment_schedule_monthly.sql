-- Parcelas ainda não lançadas da parcelada passam a cair no mesmo dia de cada mês, contado da data
-- da compra (antes: 30 dias corridos após a anterior). Só next_installment_date muda: as parcelas já
-- lançadas mantêm a data em que foram lançadas.
UPDATE installment_bill
SET next_installment_date = DATE_ADD(purchase_date, INTERVAL (installment_count + 1) MONTH)
WHERE status = 'PENDING'
  AND flag_active = 1
  AND installment_count IS NOT NULL
  AND installment_count < installment_total;
