# PersonalBudget

## Visão Geral

O **PersonalBudget** é uma aplicação desenvolvida para facilitar a gestão financeira pessoal, permitindo que os usuários acompanhem receitas, despesas e organizem suas finanças de forma eficiente e prática.

O projeto inclui funcionalidades para gerenciar diferentes tipos de movimentações financeiras, contas bancárias, cartões de crédito (com titulares, plásticos e cartão emprestado a terceiros), contas recorrentes, ciclos de pagamento, envelopes, previsão de caixa e cálculos automatizados de saldos diários. Tudo é exposto por uma API REST documentada com OpenAPI.

O front-end é o PWA [personalbudget-web](https://github.com/brunosilvaJava/personalbudget-web).

---

## Tecnologias Utilizadas

- **Java - OpenJDK (`21`)** - Linguagem de programação principal.
- **Spring Boot (`3.2.4`)** - Framework para criação de aplicações baseadas em Java.
- **MySQL (`8.0`)** - Banco de dados relacional.
- **Docker (`24.0`)** e **Docker Compose (`2.18`)** - Para orquestração de containers.
- **Gradle (`8.6`)** - Ferramenta de automação de build.
- **Flyway (`10.10.0`)** - Controle de versionamento e migração do banco de dados.
- **MapStruct (`1.5.5.Final`)** - Mapeamento de entidades e objetos.
- **OpenAPI (`3.0`, SpringDoc `2.6`)** - Documentação interativa da API, facilitando o uso e teste dos endpoints.
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

### Categorias iniciais

Uma instalação nova recebe categorias ativas organizadas nos grupos existentes. A lista é um conjunto inicial de defaults e pode ser ajustada depois pelo usuário:

| Grupo | Categorias |
|---|---|
| Residência | Aluguel/Financiamento, Água, Energia elétrica, Gás, Internet, Manutenção da casa |
| Pessoal | Alimentação, Saúde, Vestuário, Lazer, Cuidados pessoais |
| Instrução | Educação, Cursos, Livros e materiais |
| Moto | Combustível, Manutenção, Seguro, Documentação |
| Investimento | Reserva de emergência, Investimentos |
| Outros | Impostos e taxas, Doações, Outros |
| Receitas | Salário, Renda extra, Rendimentos, Reembolso, Outros |

As categorias dos seis primeiros grupos são do tipo **DEBIT**; as de **Receitas** são **CREDIT**. A migration não duplica uma categoria quando já existe a mesma combinação de nome e tipo de operação, nem modifica categorias existentes.

### Contas e transferências
- Contas com **saldo inicial** (que não é receita), uma **conta padrão** única e **extrato por conta**.
- Pagar uma movimentação é criar uma **liquidação** com valor, data efetiva e conta; o estorno marca `reversed_at`.
- **Transferência** entre contas move o caixa, mas não é lançamento (não entra em receitas nem despesas).

### Cartões de crédito
- Cartão com dia de fechamento, dia de vencimento, limite e conta padrão. A compra parcelada vira **uma movimentação DEBIT por parcela**, na fatura correspondente; pagar a fatura move o caixa. O **limite disponível** considera as parcelas ainda não pagas.
- **Titulares e plásticos:** todo cartão tem um titular (criado automaticamente) e pode ter titulares adicionais e plásticos (final de 4 dígitos). Os plásticos compartilham o limite, a fatura e o ciclo do cartão; a fatura mostra o total por titular.
- **Cartão emprestado a terceiros:** um **terceiro** pode ser definido como padrão do cartão ou do plástico, ou marcado por compra (a compra pode ser forçada como própria). A compra de terceiro **não infla** consumo nem renda: o caixa continua verdadeiro (aparece em linhas de *repasse*), e cada parcela gera um **recebível** (a receber) com vencimento e atraso.
- **A receber:** recebimento em lote (do recebível mais antigo ao mais novo, em uma conta) e alteração de vencimento. A **baixa** (desistir de cobrar) leva o saldo inteiro do recebível, não mexe no caixa e vira despesa própria na categoria fixa "Perdas com terceiros".
- **Importação em lote:** `POST /card_purchase/batch` importa até 300 compras de uma fatura, tudo ou nada, com `dry_run` (prévia por linha), deduplicação exata (`import_key`) e provável, e `first_installment` para começar no meio de um parcelamento.

### Receita e ciclos de pagamento
- **Agenda de receita** (dia fixo ou N-ésimo dia útil), **calendário de feriados** por ano e **agenda âncora**.
- **Ciclos de pagamento** `[início, fim)` ligados no modo `PAY_CYCLE`, com ajuste de fronteira, transição de âncora e **resumo por competência** (a competência é separada do caixa).
- **Fechamento** do ciclo com snapshots versionados e **reabertura**. Em ciclo fechado, alterar valor, categoria, tipo ou competência, ou excluir, retorna 409; pagar continua permitido.

### Envelopes e previsão de caixa
- **Envelopes** de gasto por categoria e ciclo.
- **Previsão de caixa** (`/forecast`), com os recebíveis de terceiros em componente próprio.

### Relatórios
- **Balanço**, **Dashboard** e `category_summary` olham o caixa e o mês de calendário, sem transferências nem saldo inicial. Compras de terceiros aparecem como **repasse**, fora de receita e despesa.
- O resumo do ciclo, os envelopes e o fechamento usam a competência e não incluem compra de terceiro nem recebível (só a baixa, como despesa própria).

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

4. **Front-end:** o PWA [personalbudget-web](https://github.com/brunosilvaJava/personalbudget-web) sobe pelo compose dele e fala com esta API pela rede do Docker (veja o README do front). Para subir a API com o banco: `docker compose --profile app up -d`.

---

## Convenções do código

- **Camadas:** Controller → Service → Repository → MySQL, com Mapper (MapStruct) transversal. Controllers não acessam repositórios; services recebem e retornam domain models (nunca entidades); domain models nunca saem na API, sempre viram `*Response` via Mapper.
- **JSON** em `snake_case`. O identificador público é o `code` (UUID); o `id` (Long) é interno.
- **Exclusão lógica:** `flagActive = false`, sem DELETE físico.
- **Status HTTP:** POST 201, GET 200, PATCH 200, DELETE 204. Erros: 400 `{message, validations}`, 404, 409 `{message}` e 500.
- **Migrations (Flyway):** `V{n}__{descricao}.sql` em `src/main/resources/db/migration/` (hoje V1 a V33). Nunca edite uma migration já aplicada. O perfil `local` roda `ddl-auto: validate`, então todo campo mapeado precisa ter o tipo da coluna (por exemplo, `TINYINT` com `@JdbcTypeCode`).

## Testes

- Unitários (JUnit 5 e Mockito) em `src/test`: `./gradlew test`.
- Integração com Testcontainers (MySQL) em `src/test_integration`: `./gradlew integrationTest`; precisam de Docker.
- O CI (GitHub Actions) roda os dois em pull requests e em push na `master`.

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
  - `amount_paid`: Valor agregado das liquidações ativas (somente leitura).
  - `movement_date`: Data da movimentação.
  - `due_date`: Data de vencimento.
  - `status`: Status da movimentação (PENDING, PAID_OUT, LATE).
  - **Campos de auditoria**:
    - `created_date`: Data de criação do registro.
    - `last_modified_date`: Data da última atualização do registro.

Pagamentos são armazenados individualmente em `movement_settlement`, com valor positivo,
data efetiva e estorno histórico. Use `POST /financial_movement/{code}/settlements` para
registrar um pagamento, `GET` no mesmo caminho para listar o histórico e
`POST /financial_movement/{code}/settlements/{settlement_code}/reversal` para estorná-lo.
`amount_paid` na movimentação é apenas um agregado compatível; `pay_date` não faz parte do
contrato atual, pois uma movimentação pode ter várias datas de pagamento.
POST/PATCH de movimentação rejeitam `amount_paid`, `pay_date` e `status` com HTTP 400;
o status é derivado do total liquidado e do vencimento. Pagamento excedente e estorno
repetido retornam HTTP 409. Caixa usa a data efetiva de cada liquidação, e a projeção
inclui somente o valor residual não liquidado.

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

#### **Outras tabelas**
Além das tabelas acima, o schema (migrations V1 a V33) inclui, entre outras:
- `movement_settlement`: liquidações de movimentações.
- Contas e transferências, e cartões: `credit_card`, `card_invoice`, `card_purchase`, `credit_card_holder`, `credit_card_plastic`.
- Terceiros e cobrança: `third_party` e `receivable_write_off`. O recebível é uma movimentação CREDIT em `financial_movement` ligada à parcela (`receivable_source_movement_id`).
- Receita e ciclos: agenda de receita, calendário de feriados e os ciclos de pagamento, com envelopes e snapshots de fechamento.

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

O job (`RecurrenceBillJob`) também roda na subida da aplicação. Ele gera as ocorrências PENDING, recupera as que foram perdidas, marca as atrasadas como LATE e atualiza os ciclos.

Essa funcionalidade garante que o fluxo financeiro do usuário esteja sempre atualizado.

---

## Contribuição

Se desejar contribuir para o projeto, siga os passos abaixo:
1. Faça um fork do repositório.
2. Crie uma branch para sua feature ou correção:
   ```bash
   git checkout -b feature/BTS-XXX/sua-feature
