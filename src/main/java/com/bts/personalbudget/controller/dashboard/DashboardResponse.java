package com.bts.personalbudget.controller.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    // Saldos
    private BigDecimal currentBalance;              // Saldo atual (hoje)
    private BigDecimal currentProjectedBalance;     // Saldo projetado atual (hoje)
    private BigDecimal monthClosingBalance;         // Saldo final do mês
    private BigDecimal monthProjectedClosingBalance; // Saldo projetado final do mês

    // Receitas do mês
    private BigDecimal monthTotalRevenue;           // Total de receitas realizadas do mês
    private BigDecimal monthProjectedTotalRevenue;  // Total projetado de receitas do mês

    // Despesas do mês
    private BigDecimal monthTotalExpense;           // Total de despesas realizadas do mês
    private BigDecimal monthProjectedTotalExpense;  // Total projetado de despesas do mês

    // Alertas
    private boolean hasNegativeBalanceAlert;        // Se haverá saldo negativo no mês
    private LocalDate firstNegativeBalanceDate;     // Primeira data com saldo negativo
    private BigDecimal lowestProjectedBalance;      // Menor saldo projetado do mês
    private LocalDate lowestBalanceDate;            // Data do menor saldo

    // Período de referência
    private LocalDate referenceMonth;               // Mês de referência (formato: YYYY-MM-01)
    private LocalDate currentDate;                  // Data atual

    // Dados adicionais
    private Integer totalDaysInMonth;               // Total de dias no mês
    private Integer daysElapsed;                    // Dias decorridos
    private Integer daysRemaining;                  // Dias restantes

    // Variações
    private BigDecimal monthNetChange;              // Variação líquida do mês (receitas - despesas)
    private BigDecimal projectedNetChange;          // Variação líquida projetada
}