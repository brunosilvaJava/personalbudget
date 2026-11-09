# Instruções para o GitHub Copilot — PersonalBudget

API REST de orçamento pessoal em Java (repo: `brunosilvaJava/personalbudget`). Pacote raiz: `com.bts.personalbudget`.

- **Idioma:** converse em português do Brasil. Código, nomes de classes, commits e títulos de PR em inglês. A descrição do PR pode ser em português.
- **O repositório é público.** Nunca comite credenciais, tokens, arquivos de configuração de ferramentas com segredos nem dados pessoais. Já existem senhas de ambiente local em `docker-compose.yml` e `run-local.sh`: não as copie para código, testes, docs ou PRs, e use variáveis de ambiente em configuração nova.
- Regras por caminho ficam em `.github/instructions/`. O agente e a skill de scaffolding ficam em `.github/agents/` e `.github/skills/`.

## Produto

O PersonalBudget controla o orçamento pessoal: receitas, despesas, contas fixas e parceladas, contas bancárias, cartões de crédito, envelopes de orçamento e previsão de caixa, com saldo real e projetado. A API é consumida por um front (PWA, repositório `personalbudget-web`), cujo E2E roda contra esta API em Docker.

| Módulo | O que é |
|---|---|
| `FinancialMovement` | Entidade central. `CREDIT`/`DEBIT` (DEBIT é gravado negativo). Status **derivado** pelo servidor: `PENDING`, `LATE` ou `PAID_OUT`. Tem `competence_date` (competência), separada da data de caixa. |
| `MovementSettlement` | Pagamento ou recebimento de um lançamento: valor positivo, data efetiva e conta. Um lançamento pode ter várias liquidações (pagamento parcial). Estorno marca `reversed_at` e nunca apaga. |
| `FixedBill` | Conta recorrente `WEEKLY`, `MONTHLY` ou `YEARLY`. `next_due_date` controla o próximo lançamento. |
| `InstallmentBill` | Parcelada com `installment_total`; `amount` é o valor de **cada** parcela. Carnê e financiamento simples; compras no cartão usam `CardPurchase`. |
| `Account` e `AccountTransfer` | Contas e carteiras, com saldo inicial (que **não é receita**). Existe sempre uma conta padrão. Transferência **não é lançamento**: só altera o saldo das duas contas. |
| `CreditCard`, `CardPurchase`, `CardInvoice` | Cartão de crédito. Cada parcela de compra é uma `FinancialMovement` DEBIT com vencimento na fatura. A compra não move caixa; o pagamento da fatura move. |
| `IncomeSchedule` e `HolidayCalendar` | Receita principal por dia fixo ou N-ésimo dia útil, com feriados locais revisados por ano. |
| `PayCycle` | Ciclos financeiros `[início, fim)` abertos pela receita âncora, com ajuste manual de fronteira, transição de âncora, resumo por competência, fechamento com snapshots versionados e reabertura. |
| `BudgetEnvelope` | Valor planejado por categoria em um ciclo. O consumo vem do resumo do ciclo. |
| `Balance`, `Dashboard` e `category_summary` | Visão de **caixa** e de mês de calendário. Não somam saldo inicial nem transferências. |
| `CashForecast` (`/forecast`) | Previsão de caixa somente leitura, com dois alertas independentes. |

`RecurrenceBillJob` roda à meia-noite (`0 0 0 * * *`, fuso `America/Sao_Paulo`) e na subida da aplicação: lança as ocorrências devidas de contas fixas, parceladas e agendas de receita (recuperando dias perdidos), marca como `LATE` as atrasadas e atualiza os ciclos.

## Stack e execução

Java 21 (use records), Spring Boot 3.2.4, Spring Data JPA/Hibernate, MySQL 8.0, Flyway, MapStruct 1.5.5.Final, Lombok 1.18.32, SpringDoc OpenAPI 2.6.0, Gradle 8.6, JUnit 5 + Mockito, TestContainers 1.19.7 (MySQL), Docker Compose.

- Imagens fixadas: `gradle:8.6-jdk21` no `Dockerfile` e `mysql:8.0` no `docker-compose.yml`. Não use tags soltas (`gradle`, `mysql:latest`): resolvem para versões incompatíveis.
- Testes: `./gradlew test` e `./gradlew integrationTest` (este precisa de Docker).
- Com Docker 29 local, a integração falha pela versão da API do TestContainers 1.19.7. O contorno `-Dapi.version=1.44` existe só numa branch sem PR (`chore/testcontainers-docker-api`); não está na `master`. O CI do GitHub não tem esse problema.
- Só o banco: `docker compose --profile dependents up -d`. Banco e aplicação: `docker compose --profile app up -d` (porta 8080).
- `./run-local.sh` sobe o MySQL e carrega os dados de `local/database/insert.sql`.
- Perfis Spring: `local` (`application-local.yml`, com `ddl-auto: validate`, usado pelo compose) e o padrão. Configuração comum em `application.yml` (`app.time-zone: America/Sao_Paulo`, Flyway habilitado). `UtilController` (`/util/*`) só existe no perfil `local` e fica fora do contrato.
- Documentação interativa: Swagger UI do SpringDoc (`/swagger-ui/index.html`).

## Arquitetura (regras que não se quebram)

```
HTTP → Controller → Service → Repository → MySQL
            └──── Mapper (MapStruct), transversal ────┘
```

- Controllers nunca acessam repositórios.
- Services recebem e retornam **domain models**, nunca entidades JPA.
- Domain models nunca saem na API: viram `*Response` via Mapper.
- Toda conversão entre camadas (Request/Response ↔ domain model ↔ entity) passa pelo Mapper. O Mapper também converte entre domínios (`FixedBill`, `InstallmentBill` → `FinancialMovement`).
- `FixedBillService` e `InstallmentBillService` têm repositórios de domínio próprios (interfaces nos subpacotes) que encapsulam o JpaRepository.
- Lógica pura (cálculo de datas, planejadores de ciclo, cálculo de fatura) fica em classes Java **sem Spring**, testáveis sem banco. Exemplos: `IncomeDateCalculator`, `IncomeOccurrenceResolver`, `PayCyclePlanner`, `PayCycleAdjustmentPlanner`, `CardInvoiceCalculator`.
- **MapStruct e datas:** neste projeto, o MapStruct não preenche `LocalDate` → `LocalDateTime` em construtor de record (isso causou o 500 corrigido no PR #102). Nesse caso, escreva o mapeamento como método `default` à mão e cubra com teste de mapper que confira as datas.

Onde as coisas ficam:
- `controller/{feature}/`: controller e DTOs, com `config/{Feature}ControllerApiDocs`. Features: `account`, `accounttransfer`, `balance`, `budgetenvelope`, `cardinvoice`, `cardpurchase`, `category`, `categorysummary`, `creditcard`, `dashboard`, `financialmovement`, `fixedbill`, `forecast`, `holidaycalendar`, `incomeschedule`, `installmentbill`, `movementsettlement`, `paycycle`. Também `common/` (`SortFieldResolver`), `validation/` e `UtilController`.
- `core/domain/entity`, `core/domain/enumerator`, `core/domain/model` (models simples, como `Category`).
- `core/domain/service/{feature}/`: `account`, `accounttransfer`, `balance`, `budgetenvelope`, `calendar`, `cardinvoice`, `cardpurchase`, `category`, `creditcard`, `dashboard`, `fixedbill`, `forecast`, `income`, `installmentbill`, `movementsettlement`, `paycycle`, `recurrencebill`. `FinancialMovementService` fica na raiz de `service/`.
- `core/domain/exception` (exceções do domínio), `exception/` (`ControllerExceptionHandler`, `ErrorResponse`, `ConflictException`, `InvalidFieldsException`), `mapper/`, `repository/`, `job/`, `config/`.

## Convenções de código

- Injeção via `@RequiredArgsConstructor` com campos `final`; Lombok para boilerplate.
- Nomes: `{Feature}Controller/Service/Entity/Repository/Mapper`, `{Feature}Request`, `{Feature}UpdateRequest`, `{Feature}Response`, `Paged{Feature}Response`, `{Feature}ControllerApiDocs`.
- Identificador público é `code` (UUID); `id` (Long) é interno.
- **Soft delete:** `flagActive = false`, nunca DELETE físico. As leituras usuais filtram por `flagActive = true`. **Exceção:** as consultas de idempotência da recorrência (`existsByRecurrenceBillCodeAndOccurrenceDate` e a consulta equivalente por intervalo) não filtram, porque um lançamento excluído conta como já gerado e não pode ser recriado.
- **Estorno:** liquidação, transferência e pagamento de fatura são estornados com `reversed_at`, sem apagar o registro.
- Log no início de todo método público: `log.info("m=metodo, param={}", valor)`.
- Escrita com `@Transactional`.
- Exceções: `EntityNotFoundException` → 404 ("Registro não encontrado"); `ConflictException` → 409; `InvalidFieldsException` → 400 com `validations`; lock otimista (`OptimisticLockingFailureException`, `OptimisticLockException`) → 409 genérico; o resto → 500 genérico. Reuse as exceções de `com.bts.personalbudget.exception`; não crie equivalentes. Não vaze nomes de classes internas nas mensagens.

## API e contrato

- DTOs são records com JSON em `snake_case` via `@JsonProperty`; validação com `@Valid`.
- Status: POST 201 (com corpo), GET 200, PATCH 200, DELETE 204 (idempotente onde o contrato diz).
- Erros: 400 `{message, validations}` com chaves em snake_case; 404 `{message: "Registro não encontrado"}`; 409 `{message}`; 500 `{message: "Erro interno no servidor"}`. Um 409 de lock otimista significa que nada foi gravado: o cliente recarrega e tenta de novo.
- **Pagamento:** `POST` e `PATCH` de movimentação **não aceitam** `amount_paid`, `pay_date` nem `status` (400). Pagar é `POST /financial_movement/{code}/settlements`. O status é derivado pelo servidor. A parcela de cartão não se paga por liquidação: paga-se a fatura.
- Listas: `page` (a partir de 0), `size`, `sort_by`, `sort_direction`. Campo de ordenação desconhecido é 400 (use `SortFieldResolver`). Resposta paginada: `content, page, size, total_elements, total_pages, first, last`. Movimentações, contas fixas e parceladas são paginadas; categorias, contas, cartões, transferências, compras, agendas de receita, ciclos e envelopes de um ciclo são listas simples.
- Datas: `LocalDate` e `LocalDateTime` sem fuso; mês como `yyyy-MM`. Valores chegam positivos na API; DEBIT é gravado negativo; liquidação é sempre positiva.
- Intervalos de ciclo são `[início, fim)`. Extratos, relatórios por período e consultas de transferência e compra são inclusivos e limitados a 366 dias.
- **Mudança de contrato** (campo novo, campo obrigatório, campo removido, status novo, endpoint novo, comportamento novo) deve estar descrita no PR. O front consome o contrato e confere `src/api/contracts.ts` contra o OpenAPI do back (`check:contract`).

## Banco e migrations

- Flyway: `V{n}__{descricao}.sql` em `src/main/resources/db/migration/`. A `master` tem **V1 a V25**; confira sempre o maior número antes de criar a próxima (a próxima livre é **V26**).
- Resumo das migrations:

| Migration | Conteúdo |
|---|---|
| V1 a V4 | `financial_movement`, `installment_bill`, `fixed_bill`, `calendar_fixed_bill` |
| V5 | `occurrence_date` e unicidade `(recurrence_bill_code, occurrence_date)` |
| V6 a V7 | `category_group`, `category` e vínculo de categoria nos lançamentos e contas |
| V8 | `competence_date` e `competence_overridden` |
| V9 e V10 | Feriados e revisão anual; agenda de receita e suas ocorrências |
| V11 e V12 | Configuração e ciclos financeiros; histórico de ajustes (só inserção) |
| V13 a V15 | Grupo "Receitas" e seed inicial de 28 categorias |
| V16 e V17 | Lifecycle `CLOSED` e snapshots de fechamento |
| V18 | `movement_settlement` (liquidações). Falha de propósito quando o dado legado não pode ser conciliado |
| V19 a V20 | Contas (com conta padrão única) e transferências entre contas |
| V21 a V23 | Cartão de crédito, compras e faturas; vínculo das parcelas com a compra e a fatura |
| V24 | Pagamento de fatura (um pagamento ativo por fatura) |
| V25 | Envelopes de orçamento por categoria e ciclo |

- Nunca edite uma migration já aplicada. Mudanças de esquema são expansivas: coluna anulável → backfill → conferência → `NOT NULL` em migration posterior.
- Regras de unicidade condicional usam coluna gerada (nula fora do estado ativo) com `UNIQUE`, mais `CHECK` para invariantes.
- O perfil `local` valida o esquema contra o mapeamento. Todo campo mapeado precisa ter o tipo da coluna (ex.: `TINYINT` com `@JdbcTypeCode`).

## Tempo, competência e caixa

- Use o `Clock` injetado nos services (`America/Sao_Paulo`, propriedade `app.time-zone`): `LocalDate.now(clock)`. Não chame `LocalDate.now()` sem argumento em services nem no job.
- **Competência e caixa são conceitos separados.**
  - Relatórios de caixa: `/balance/daily`, `/dashboard` e `/category_summary`. Usam a data efetiva de cada liquidação; o residual pendente vai para a projeção. Não os troque para competência.
  - Relatório de ciclo: `/pay_cycle/{code}/summary`, por competência.
  - Saldos das contas e `/forecast`: caixa das contas.
- **Cartão:** cada parcela tem `due_date` e `competence_date` iguais ao vencimento da fatura em que cai; a competência é manual para nunca ser recalculada. A compra nunca move caixa: o pagamento da fatura move, uma vez.
- **Transferência** não aparece em `/balance/daily`, `/dashboard`, `/category_summary` nem nos ciclos.
- Recorrência é idempotente por `(recurrence_bill_code, occurrence_date)`. Um lançamento gerado e excluído logicamente não é recriado. O job processa cada conta em transação própria (`REQUIRES_NEW`) com `try/catch`, e uma conta com erro não derruba as outras.

## Concorrência

- **Ciclos:** ordem de bloqueio configuração → agenda âncora → ciclo. Nenhum caminho pega a agenda e depois a configuração. `@Version` em configuração e ciclo; a API recebe `expected_version`; versão divergente é 409 e nada é gravado.
- **Cartão:** ordem de bloqueio guarda de ciclo (`PayCycleMovementGuard.lock()`) → linha do cartão (`PESSIMISTIC_WRITE`) → parcelas da fatura.
- **Liquidações:** escritas serializadas no lançamento, para que o total pago nunca passe do valor. Excedente é 409.
- Relatórios de totais rodam em `REPEATABLE_READ` somente leitura, para que os números de uma mesma resposta sejam coerentes.

## Testes

- JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`, `@InjectMocks`, `@Mock`, `@Spy` no mapper). Nome: `should{Ação}{Condição}`.
- Factories em `core/domain/factory/` (`AccountFactory`, `AccountTransferFactory`, `BudgetEnvelopeFactory`, `CardPurchaseFactory`, `CategoryFactory`, `CreditCardFactory`, `DailyBalanceFactory`, `FinancialMovementFactory`, `FixedBillFactory`, `HolidayCalendarFactory`, `IncomeScheduleFactory`, `PayCycleFactory`).
- Unitários em `src/test`; integração com TestContainers MySQL em `src/test_integration` (inclui `*MigrationTest`, `*RepositoryTest`, `*IntegrationTest` e `SchemaValidationIntegrationTest`).
- Lógica nova leva testes unitários. Persistência (constraints, colunas geradas, `@Version`, bloqueios, migrations) leva teste de integração.
- Mapper com data: o teste confere as datas (`movement_date`, `due_date`, `occurrence_date`), não só os outros campos.
- Testes de integração compartilham o banco: os que medem caixa comparam o antes e o depois, para não depender dos dados de outros testes.
- O CI (`.github/workflows/ci.yml`) roda `./gradlew test` e `./gradlew integrationTest` em todo `pull_request` e em push na `master`. Branch sem PR não tem CI.
- Se não conseguir rodar os testes, diga isso na descrição do PR em vez de afirmar que passaram.

## Fluxo de trabalho

- Um passo por PR, a partir da `master`. Branches `feature/...`, `fix/...`, `chore/...` ou `docs/...`. Commits em inglês, no imperativo.
- Não faça merge nem force-push.
- Descrição do PR segue `.github/PULL_REQUEST_TEMPLATE.md`: resumo, mudanças de contrato, migrations, testes (o que rodou e o que não rodou) e o que ficou fora do escopo.
- Todo PR de uma etapa atualiza a seção "Estado atual" abaixo.
- Cada etapa da fila tem um brief autocontido (arquivo `.md` fornecido na tarefa). Se o brief conflitar com o código, pare e pergunte.

## Estado atual (atualizar a cada etapa)

Conferido em 2026-11-09 sobre a `master` em `1d7c0fa`.

- **Back, concluído (E0 a E11):**
  - Competência (E1), calendário de feriados e agenda de receita (E2 e E3), ciclos financeiros (E4a a E4c), resumo por competência (E5), fechamento com snapshots e reabertura (E6), liquidações parciais e estorno (E7), contas e transferências (E8), cartões: cadastro, compras e faturas, pagamento de fatura e vínculo nos lançamentos (E9), envelopes (E10) e previsão de caixa (E11).
  - Extrato da conta: `GET /account/{code}/statement` (PR #99).
  - Correções achadas pelo E2E do front: imagens do Gradle e do MySQL fixadas (PR #100), `closing_day` e `due_day` como `TINYINT` (PR #101), datas do lançamento gerado por conta fixa e parcelada (PR #102).
- **Front, concluído:** Fases 1a a 8b e a PR #17 (remoção dos contornos do E2E), mergeada em 2026-11-09. Fase 9 (multiusuário) adiada.
- **Pendências do back:**
  1. **Bug D:** a parcelada antiga gera parcelas a cada 30 dias corridos. A conversão das parceladas antigas para o modelo de cartão está fora do escopo.
  2. **Fora do escopo por enquanto:** pagamento parcial de fatura, conta fixa no cartão e parcelada antiga no cartão.
  3. **Lacunas do E1:** recorrentes antigas sem `occurrence_date` ficam sem `competence_date`. Falta a regra de recuperação e a conferência antes do `NOT NULL`, que não é automatizada.
  4. **Não reverificado:** `InstallmentBill.update` pode não recalcular `next_installment_date` quando `purchase_date` muda; `FixedBillService.updateNextDueDate` e `InstallmentBillService.updateNextInstallmentDate` são itens de revisão antigos.
  5. **DELETE repetido:** de movimentação e de parcelada é idempotente (204). De conta fixa, o último registro dizia que respondia 404; conferir e alinhar.
  6. **Contrato do front:** `nominal_month` de `PayCycleSummaryPendingResponse` pode divergir entre o `contracts.ts` e o OpenAPI do back (aviso do `check:contract`, não investigado).
  7. **Categorias:** a lista do seed da V15 foi montada pelo Copilot e precisa ser conferida pelo dono. Em aberto: o grupo "Investimento" da V6 e a natureza das categorias (consumo, receita, receita financeira, despesa financeira).
  8. **Infra:** a branch `chore/testcontainers-docker-api` está sem PR (o CI já passa). Branches antigas para apagar: `brunosilvajava-arch-analysis`, `test`, `chore/env-credentials` e `tmp/verify-schema-test`.
- **Fora do escopo:** multiusuário (`owner_user_id`). O Dashboard mensal não é adaptado para ciclos.

## O que não fazer

- Não expor entidades JPA na API nem fazer o controller falar com repositório.
- Não editar migrations já aplicadas, nem usar DELETE físico.
- Não trocar caixa por competência nos relatórios existentes.
- Não usar `LocalDate.now()` sem `Clock` em services ou no job.
- Não criar `owner_user_id` nem login sem pedido explícito.
- Não comitar segredos, nem afirmar que testes passaram sem tê-los rodado.
