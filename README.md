# PersonalBudget

## Visão Geral

O **PersonalBudget** é uma aplicação desenvolvida para facilitar a gestão financeira pessoal, permitindo que os usuários acompanhem receitas, despesas e organizem suas finanças de forma eficiente e prática.

O projeto inclui funcionalidades para gerenciar diferentes tipos de movimentações financeiras, contas recorrentes e cálculos automatizados de saldos diários. Também oferece suporte à visualização de dados financeiros por meio de uma API bem estruturada.

---

## Tecnologias Utilizadas

- **Java - OpenJDK (`21`)** - Linguagem de programação principal.
- **Spring Boot (`3.2.4`)** - Framework para criação de aplicações baseadas em Java.
- **MySQL (`8.0`)** - Banco de dados relacional.
- **Docker (`24.0`)** e **Docker Compose (`2.18`)** - Para orquestração de containers.
- **Gradle (`8.6`)** - Ferramenta de automação de build.
- **Flyway (`10.10.0`)** - Controle de versionamento e migração do banco de dados.
- **MapStruct (`1.5.5.Final`)** - Mapeamento de entidades e objetos.
- **OpenAPI (`3.0`)** - Documentação interativa da API, facilitando o uso e teste dos endpoints.
- **Lombok (`1.18.32`)** - Redução de boilerplate no código.
- **TestContainers (`1.19.7`)** - Biblioteca para testes de integração utilizando containers.
- **Log4j (`2.23.1`)** - Framework de logging para gerenciamento e monitoramento de logs.
- **Hibernate (`6.2.6`)** - ORM para comunicação entre a aplicação e o banco de dados.
- **JUnit 5** - Framework de testes para garantir a qualidade do código.
- **Feign Client** - Cliente HTTP para comunicação entre microservices.

---

## Funcionalidades Principais

### Movimentações Financeiras
- Cadastro de receitas e despesas.
- Visualização, edição e exclusão de movimentações financeiras.
- Suporte a tipos de operações: **CREDIT** (crédito) e **DEBIT** (débito).
- Estados de movimentação: **PENDING** (pendente), **PAID_OUT** (pago) e **LATE** (atrasado).

### Contas Recorrentes

O PersonalBudget permite o gerenciamento de **contas recorrentes**, que são movimentações financeiras programadas para ocorrer periodicamente. Existem dois tipos principais de contas recorrentes:

1. **Contas Fixas**:
   - Movimentações financeiras que ocorrem regularmente (ex.: aluguel, assinaturas, contas de consumo).
   - Pode ter recorrência **semanal**, **mensal** ou **anual**.

2. **Contas Parceladas**:
   - Movimentações financeiras associadas a uma compra ou compromisso dividido em parcelas (ex.: financiamento, compras parceladas no cartão de crédito).
   - A cada parcela paga, a próxima parcela é automaticamente programada.

#### Automatização de Contas Recorrentes

O sistema realiza a **automatização** do lançamento de contas recorrentes através de um job agendado que roda diariamente. Este processo:
- Lança automaticamente movimentações financeiras baseadas em contas fixas e parceladas com vencimento no dia.
- Atualiza as próximas datas de vencimento para contas recorrentes.

### Balanço Diário

O PersonalBudget fornece a funcionalidade de **Balanço Diário**, permitindo que os usuários acompanhem:
- **Saldo de abertura**: saldo acumulado até o início do dia.
- **Receitas e despesas do dia**.
- **Saldo de fechamento**: saldo acumulado após todas as receitas e despesas do dia.
- **Balanço projetado**: inclui movimentações pendentes para dias futuros.

A funcionalidade é altamente flexível, permitindo o acompanhamento de saldos entre um intervalo de datas especificado. Essa visão consolidada ajuda o usuário a entender seu fluxo financeiro diário e tomar decisões mais informadas.

---

## Como Executar o Projeto Localmente

1. **Pré-requisitos**:
   - Docker e Docker Compose instalados.
   - Java 21 instalado.
   - Gradle configurado no ambiente (opcional).

2. **Configuração Inicial**:
   - Clone o repositório do projeto.
   - Configure variáveis de ambiente (ex.: `DB_USERNAME`, `DB_PASSWORD`) no arquivo `.env` ou exporte-as diretamente no terminal.

3. **Executar com o Script `run-local.sh`**:
   - Rode o script:
     ```bash
     ./run-local.sh
     ```
   - Esse script cuida de iniciar os containers e inserir dados iniciais no banco.

---

## Documentação da API

A aplicação utiliza **OpenAPI** para fornecer documentação interativa. Após iniciar o sistema, você pode acessar a documentação em:

http://localhost:8080/swagger-ui/index.html

---

## Estrutura do Banco de Dados

### Tabelas e Descrição

#### **1. Movimentações Financeiras**
- **Tabela**: `financial_movement`
- **Descrição**: Armazena as movimentações financeiras registradas pelos usuários.
- **Campos principais**:
  - `id`: Identificador único (chave primária).
  - `code`: Código UUID da movimentação.
  - `operation_type`: Tipo de operação (CREDIT ou DEBIT).
  - `description`: Descrição da movimentação.
  - `amount`: Valor total da movimentação.
  - `amount_paid`: Valor pago.
  - `movement_date`: Data da movimentação.
  - `due_date`: Data de vencimento.
  - `pay_date`: Data de pagamento.
  - `status`: Status da movimentação (PENDING, PAID_OUT, LATE).
  - **Campos de auditoria**:
    - `created_date`: Data de criação do registro.
    - `last_modified_date`: Data da última atualização do registro.

#### **2. Parcelas**
- **Tabela**: `installment_bill`
- **Descrição**: Representa compras parceladas ou financiamentos.
- **Campos principais**:
  - `id`: Identificador único (chave primária).
  - `code`: Código UUID da conta parcelada.
  - `description`: Descrição da conta parcelada.
  - `amount`: Valor total da conta.
  - `installment_total`: Total de parcelas.
  - `installment_count`: Número de parcelas já pagas.
  - `last_installment_date`: Data da última parcela paga.
  - `next_installment_date`: Data da próxima parcela.
  - **Campos de auditoria**:
    - `created_date`: Data de criação do registro.
    - `last_modified_date`: Data da última atualização do registro.

#### **3. Contas Fixas**
- **Tabela**: `fixed_bill`
- **Descrição**: Registra as contas fixas e recorrentes (ex.: assinaturas, contas de consumo).
- **Campos principais**:
  - `id`: Identificador único (chave primária).
  - `code`: Código UUID da conta fixa.
  - `description`: Descrição da conta fixa.
  - `amount`: Valor da conta fixa.
  - `operation_type`: Tipo da operação (CREDIT ou DEBIT).
  - `status`: Status da conta (ACTIVE ou INACTIVE).
  - `start_date`: Data de início.
  - `end_date`: Data de término.
  - `recurrence_type`: Tipo de recorrência (WEEKLY, MONTHLY ou YEARLY).
  - `next_due_date`: Próxima data de vencimento.
  - **Campos de auditoria**:
    - `created_date`: Data de criação do registro.
    - `last_modified_date`: Data da última atualização do registro.

#### **4. Calendário de Contas Fixas**
- **Tabela**: `calendar_fixed_bill`
- **Descrição**: Armazena os dias de recorrência das contas fixas.
- **Campos principais**:
  - `id`: Identificador único (chave primária).
  - `day_launch`: Dia do lançamento.
  - `flg_leap_year`: Indica se a conta considera anos bissextos.
  - `id_fixed_bill`: Referência à tabela `fixed_bill`.
  - **Campos de auditoria**:
    - `created_date`: Data de criação do registro.
    - `last_modified_date`: Data da última atualização do registro.

---

## Automatização e Jobs Agendados

O sistema possui um **job automatizado** para processar contas recorrentes. Ele é executado diariamente, utilizando a seguinte configuração de agendamento:
- Cron expression: `0 0 0 * * *` (executa à meia-noite todos os dias).

Funções do job:
- Lançar contas fixas e parcelas programadas com vencimento para o dia atual.
- Atualizar as datas das próximas parcelas e vencimentos.

Essa funcionalidade garante que o fluxo financeiro do usuário esteja sempre atualizado.

---

## Contribuição

Se desejar contribuir para o projeto, siga os passos abaixo:
1. Faça um fork do repositório.
2. Crie uma branch para sua feature ou correção:
   ```bash
   git checkout -b feature/BTS-XXX/sua-feature

