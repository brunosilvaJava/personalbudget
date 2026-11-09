# Instruções para o GitHub Copilot — PersonalBudget

API REST de orçamento pessoal em Java (repo: `brunosilvaJava/personalbudget`). Pacote raiz: `com.bts.personalbudget`.

- **Idioma:** converse em português do Brasil. Código, nomes de classes, commits e títulos de PR em inglês. A descrição do PR pode ser em português.
- **O repositório é público.** Nunca comite credenciais, tokens, arquivos de configuração de ferramentas com segredos nem dados pessoais. Já existem senhas fixas em `docker-compose.yml` e `run-local.sh`: não as copie para código, testes, docs ou PRs, e use variáveis de ambiente em configuração nova.
- Regras por caminho ficam em `.github/instructions/`. O agente e a skill de scaffolding ficam em `.github/agents/` e `.github/skills/`.

## Produto

O PersonalBudget controla o orçamento pessoal: receitas, despesas, contas fixas recorrentes, compras parceladas e saldo diário real e projetado. A API é consumida por um front (PWA, repositório `personalbudget-web`).

| Módulo | O que é |
|---|---|
| `FinancialMovement` | Entidade central. `CREDIT`/`DEBIT`; status `PENDING`, `LATE`, `PAID_OUT`. As outras fontes geram movimentações. |
| `FixedBill` | Conta recorrente `WEEKLY`, `MONTHLY` ou `YEARLY`. O mensal usa `calendar_fixed_bill` para os dias; `next_due_date` controla o próximo lançamento. |
| `InstallmentBill` | Compra parcelada (`installment_total`, `installment_count`); `amount` é o valor de **cada** parcela; encerra quando todas as parcelas são lançadas. |
| `Balance` e `Dashboard` | Saldo diário real (pagos) e projetado (pendentes, atrasados e ocorrências ainda não lançadas); visão do mês. |
| `Category` | Grupos e categorias opcionais nos lançamentos; resumo por categoria (de caixa). |
| `IncomeSchedule` e `HolidayCalendar` | Receita principal por dia fixo ou N-ésimo dia útil, com feriados locais revisados por ano. |
| `PayCycle` | Ciclos financeiros `[início, fim)` abertos pela receita âncora, com ajuste manual e histórico. |

`RecurrenceBillJob` roda à meia-noite (`0 0 0 * * *`) e na subida da aplicação: lança as ocorrências devidas de contas fixas, parceladas e agendas de receita (com recuperação de dias perdidos), avança as próximas datas e atualiza os ciclos.

## Stack e execução

Java 21 (use records), Spring Boot 3.2.4, Spring Data JPA/Hibernate, MySQL 8.0, Flyway, MapStruct 1.5.5.Final, Lombok 1.18.32, SpringDoc OpenAPI 2.6.0, Gradle 8.6, JUnit 5 + Mockito, TestContainers 1.19.7 (MySQL), Docker Compose.

- Testes: `./gradlew test` e `./gradlew integrationTest` (este precisa de Docker).
- Só o banco: `docker compose --profile dependents up -d`. Banco e aplicação: `docker compose --profile app up -d` (porta 8080).
- `./run-local.sh` sobe o MySQL e carrega os dados de `local/database/insert.sql`.
- Perfis Spring: `local` (`application-local.yml`) e o padrão. Configuração comum em `application.yml` (`app.time-zone`, Flyway habilitado). `UtilController` (`/util/*`) só existe no perfil `local`.
- Documentação interativa: Swagger UI do SpringDoc (`/swagger-ui/index.html`).

## Arquitetura (regras que não se quebram)

```
HTTP → Controller → Service → Repository → MySQL
            └──── Mapper (MapStruct), transversal ────┘
```

- Controllers nunca acessam repositórios.
- Services recebem e retornam **domain models**, nunca entidades JPA.
- Domain models nunca saem na API: viram `*Response` via Mapper.
- Toda conversão entre camadas (Request/Response ↔ domain model ↔ entity) passa pelo Mapper. O Mapper também converte entre domínios (`FixedBill` e `InstallmentBill` → `FinancialMovement`).
- `FixedBillService` e `InstallmentBillService` têm repositórios de domínio próprios (interfaces nos subpacotes) que encapsulam o JpaRepository.
- Lógica pura (cálculo de datas, planejadores de ciclo) fica em classes Java **sem Spring**, testáveis sem banco. Exemplos: `IncomeDateCalculator`, `PayCyclePlanner`, `PayCycleAdjustmentPlanner`.

Onde as coisas ficam: `controller/{feature}/` (controller e DTOs, com `config/{Feature}ControllerApiDocs`), `core/domain/entity`, `core/domain/enumerator`, `core/domain/model` (models simples, como `Category`), `core/domain/service/{feature}/` (services e os models de features com regra própria: `fixedbill`, `installmentbill`, `income`, `calendar`, `paycycle`, `balance`, `category`, `dashboard`, `recurrencebill`), `core/domain/exception` (exceções específicas do domínio), `exception/` (exceções e respostas de erro da API: `ConflictException`, `InvalidFieldsException`, `ErrorResponse`, `ControllerExceptionHandler`), `mapper/`, `repository/`, `job/`, `config/`.

## Convenções de código

- Injeção via `@RequiredArgsConstructor` com campos `final`; Lombok para boilerplate.
- Nomes: `{Feature}Controller/Service/Entity/Repository/Mapper`, `{Feature}Request`, `{Feature}UpdateRequest`, `{Feature}Response`, `Paged{Feature}Response`, `{Feature}ControllerApiDocs`.
- Identificador público é `code` (UUID); `id` (Long) é interno.
- Soft delete: `flagActive = false`, nunca DELETE físico. As leituras usuais filtram por `flagActive = true`. **Exceção:** as consultas de idempotência da recorrência (`existsByRecurrenceBillCodeAndOccurrenceDate` e a consulta equivalente por intervalo) não filtram, porque um lançamento excluído conta como já gerado e não pode ser recriado.
- Log no início de todo método público: `log.info("m=metodo, param={}", valor)`.
- Escrita com `@Transactional`.
- Exceções: `EntityNotFoundException` → 404 ("Registro não encontrado"); `ConflictException` → 409; `InvalidFieldsException` → 400 com `validations`; o resto → 500 genérico. Reuse as exceções de `com.bts.personalbudget.exception`; não crie equivalentes. Não vaze nomes de classes internas nas mensagens.

## API e contrato

- DTOs são records com JSON em `snake_case` via `@JsonProperty`; validação com `@Valid`.
- Status: POST 201 (com corpo), GET 200, PATCH 200, DELETE 204.
- Erros de validação: 400 `{message, validations}` com chaves em snake_case.
- Listas: `page` (a partir de 0), `size`, `sort_by`, `sort_direction`. Campo de ordenação desconhecido é 400 (use `SortFieldResolver`). Resposta paginada: `content, page, size, total_elements, total_pages, first, last`.
- Datas: `LocalDate` e `LocalDateTime` sem fuso; mês como `yyyy-MM`. Valores chegam positivos na API; DEBIT é gravado negativo.
- **Mudança de contrato** (campo novo, campo obrigatório, status novo, endpoint novo, comportamento novo) deve estar descrita no PR: o front consome o contrato.

## Banco e migrations

- Flyway: `V{n}__{descricao}.sql` em `src/main/resources/db/migration/`. A `master` tem V1 a V25; confira sempre o maior número antes de criar a próxima (a próxima livre é V26).
- Nunca edite uma migration já aplicada. Mudanças de esquema são expansivas: coluna anulável → backfill → conferência → `NOT NULL` em migration posterior.
- Regras de unicidade condicional usam coluna gerada (nula fora do estado ativo) com `UNIQUE`, mais `CHECK` para invariantes.

## Tempo, competência e caixa

- Use o `Clock` injetado nos services (`America/Sao_Paulo`, propriedade `app.time-zone`): `LocalDate.now(clock)`. Não chame `LocalDate.now()` sem argumento em services nem no job.
- Intervalos novos são `[início, fim)`: a data do fim pertence ao período seguinte.
- **Competência e caixa são conceitos separados.** `/balance/daily`, `/dashboard` e `/category_summary` são de caixa e de mês de calendário; não os troque para competência. Os ciclos têm contrato próprio.
- Recorrência é idempotente por `(recurrence_bill_code, occurrence_date)`. Um lançamento gerado e excluído logicamente não é recriado. O job processa cada conta em transação própria (`REQUIRES_NEW`) com `try/catch`, e uma conta com erro não derruba as outras.

## Concorrência (ciclos financeiros)

- Ordem de bloqueio: configuração → agenda âncora → ciclos. Nenhum caminho pega a agenda e depois a configuração.
- `@Version` em configuração e ciclo; a API recebe `expected_version`; versão divergente é 409 e nada é gravado.

## Testes

- JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`, `@InjectMocks`, `@Mock`, `@Spy` no mapper). Nome: `should{Ação}{Condição}`.
- Factories em `core/domain/factory/`. Unitários em `src/test`; integração com TestContainers em `src/test_integration`.
- O CI (`.github/workflows/ci.yml`) roda `./gradlew test` e `./gradlew integrationTest` em todo PR.
- Lógica nova leva testes. Para persistência (constraints, colunas geradas, `@Version`, bloqueios), use teste de integração.
- Se não conseguir rodar os testes, diga isso na descrição do PR em vez de afirmar que passaram.

## Fluxo de trabalho

- Um passo por PR, a partir da `master`. Branches `feature/...`, `fix/...` ou `chore/...`. Commits em inglês, no imperativo. Não faça merge.
- Descrição do PR segue `.github/PULL_REQUEST_TEMPLATE.md`: resumo, mudanças de contrato, testes (o que rodou e o que não rodou) e o que ficou fora do escopo.
- Todo PR de uma etapa atualiza a seção "Estado atual" abaixo.
- Cada PR da fila tem um brief autocontido (arquivo `.md` fornecido na tarefa). Se o brief conflitar com o código, pare e pergunte.

## Estado atual (atualizar a cada etapa)

- `master` inclui o pacote de ajustes do backend (PR #79), migrations V1 a V15 e o ciclo financeiro até a **E4c** (PR #81). A camada 1 da E4c foi entregue no PR #80: guarda da agenda âncora, transição com histórico e V14. A camada 2 foi entregue no PR #81: prévia de fronteiras/divergências do calendário e refresh em `REQUIRES_NEW` após commit. A prévia retorna um objeto com `income_occurrences`, `cycle_boundaries` e `cycle_divergences`, em vez do array na raiz (quebra de contrato autorizada).
- **Seed inicial de categorias (PR #83):** a V15 adiciona defaults ativos e editáveis para despesas em Residência, Pessoal, Instrução, Moto, Investimento e Outros, além de receitas em Receitas. A migration só insere uma combinação de nome e tipo de operação que ainda não exista, preservando categorias já cadastradas.
- **Relatório por ciclo financeiro (E5):** `GET /pay_cycle/{code}/summary` resume receitas e despesas por `competence_date`, com valores reconhecidos (`PAID_OUT`), projetados (reconhecidos, `PENDING`/`LATE` e ocorrências virtuais conhecidas), detalhe por categoria e `projection_incomplete`/`pending`. Débitos são positivos nesta resposta; sem categoria usa `Sem categoria`. O intervalo é `[start_date, end_date_exclusive)`. A projeção não declara como completos saldos, transferências, cartões ou envelopes que ainda não fazem parte do domínio. `/balance/daily`, `/dashboard` e `/category_summary` continuam sendo relatórios de caixa e não mudam.
- **Fechamento de ciclos financeiros (E6):** a camada 1 (PR #85) introduz `CLOSED` como lifecycle/status derivado e amplia a migration V16 para manter ciclos fechados na cadeia e nas chaves de unicidade. A camada intermediária (PR #86) acrescenta a V17 e snapshots imutáveis e versionados; a camada final expõe prévia, fechamento, reabertura e histórico de snapshots. O fechamento bloqueia pendências de projeção e movimentações `LATE`, mas não somente a presença de obrigações `PENDING`; ciclo fechado exige reabertura antes de mudanças de valor, categoria, tipo, competência ou exclusão, enquanto registrar pagamento posterior é permitido. Fechar calcula e persiste o snapshot na mesma transação e ordem de locks configuração → agenda âncora → ciclo.
- **E7 — fundamento de liquidações (camada inferior):** a migration V18 cria `movement_settlement` e concilia `amount_paid`/`pay_date` legados como liquidações positivas com data efetiva. A migration falha se os campos legados não permitirem conciliação segura. A entidade e o modelo representam cada pagamento e seu estorno sem exclusão física; a API e a mudança dos relatórios de caixa ficam para a camada superior empilhada.
- **E7 — API e totais (camada superior sobre #89):** a API registra, lista e estorna liquidações sem apagar histórico. `amount_paid` permanece agregado na resposta por compatibilidade, mas pagamento/status não são aceitos nas escritas de movimentação e `pay_date` não é mais exposto; `PAID_OUT` só ocorre quando a soma ativa alcança o valor total, com escritas serializadas para impedir pagamento excedente. Saldo diário, Dashboard e resumo por categoria reconhecem caixa pela data efetiva de cada liquidação; o residual mantém sua competência e projeção. E5 reconhece as liquidações pela competência da movimentação, sem duplicar o residual, e preserva `[início, fim)`, débitos positivos e pendências de projeção. Pagamentos posteriores ao fechamento continuam permitidos sem alterar snapshots imutáveis.
- **Pacote de ajustes do back para o front entregue:** respostas da parcelada incluem `installment_count` e `next_installment_date`; alterar `purchase_date` recalcula a próxima data apenas antes da primeira parcela, total atualizado igual ao contador conclui a conta, e o PATCH lança parcelas vencidas na mesma transação; DELETE de movimentação e parcelada é idempotente; movimentações vencidas viram `LATE` ao criar/editar e no job, e o update volta para `PENDING` quando o vencimento passa para hoje ou depois; grupo de categorias "Receitas" adicionado na V13.
- `installment_total` não pode ser reduzido abaixo de `installment_count`; o job atualiza `last_modified_date` junto com o status. DELETE repetido de conta fixa ainda responde 404; pode ser alinhado à idempotência em uma etapa posterior.
- `amount` da parcelada é o valor de cada parcela.
- A alteração da regra da âncora ativa em `PAY_CYCLE` só ocorre por `/pay_cycle_config/anchor_transition`; os endpoints diretos da agenda retornam 409 para mudanças protegidas. A transição preserva ciclos iniciados e registra `ANCHOR_TRANSITION`. Quando a primeira ocorrência da nova agenda coincide com a fronteira congelada, ela abre um ciclo regular sem transição.
- O histórico de `ANCHOR_TRANSITION` delimita o prefixo preservado: o refresh e as pendências não reaplicam a regra atual aos ciclos anteriores à transição, inclusive quando a agenda mantém o mesmo código.
- **E8a — contas e saldo inicial (V19):** tabela `account` (`code`, `name` único, `type` `CHECKING`/`CASH`/`SAVINGS`/`OTHER`, `initial_balance`, `initial_balance_date`, `status`, `is_default`). O saldo inicial **não é receita**. Existe sempre uma única conta padrão (coluna gerada com `UNIQUE`); a V19 cria "Conta principal" (saldo 0 em 2000-01-01) e liga a ela todas as liquidações existentes. `movement_settlement.account_id` é obrigatório; `POST /financial_movement/{code}/settlements` aceita `account_code` opcional (omitido = conta padrão; conta inexistente ou inativa = 400; `effective_date` antes do saldo inicial da conta = 400) e a resposta traz `account_code`. API: `POST/GET /account`, `GET/PATCH /account/{code}` (`default: true` troca a conta padrão), `PATCH /account/{code}/inactivate|activate` (a conta padrão não pode ser inativada: 409) e `GET /account/{code}/balance?date=` (saldo inicial + liquidações ativas da conta de `initial_balance_date` até a data; antes dessa data, zero). `/balance/daily`, `/dashboard` e `/category_summary` não mudam. Transferências e saldo total por conta ficaram para o E8b (abaixo).
- **E8b — transferências entre contas (V20):** tabela `account_transfer` (`code`, `from_account_id`, `to_account_id`, `amount` > 0, `effective_date`, `description`, `reversed_at`, `version`; CHECK impede origem = destino). A transferência **não é lançamento**: é neutra para consumo e nunca aparece em `/balance/daily`, `/dashboard`, `/category_summary` nem nos ciclos; só altera o saldo das duas contas. API: `POST /account_transfer` (201; as duas contas ACTIVE e `effective_date` não anterior ao `initial_balance_date` de nenhuma delas, senão 400), `GET /account_transfer?start_date&end_date[&account_code]` (período inclusivo de até 366 dias, inclui estornadas, sem paginação), `GET /account_transfer/{code}` e `POST /account_transfer/{code}/reversal` (marca `reversed_at` sem apagar; repetir = 409). O saldo da conta passou a ser saldo inicial + liquidações ativas + transferências líquidas (entradas − saídas ativas) de `initial_balance_date` até a data; a resposta de `GET /account/{code}/balance` ganhou `transfers_total`. `GET /account/balance?date=` devolve o saldo de todas as contas (ACTIVE e INACTIVE, padrão primeiro) e o `total`, em que as transferências se anulam. `initial_balance_date` também não pode passar da primeira transferência ativa da conta.
- **E9a — cartão de crédito, só cadastro (V21):** tabela `credit_card` (`code`, `name` único, `closing_day` e `due_day` de 1 a 31 com CHECK, `credit_limit` opcional > 0, `default_account_id` opcional, `status` `ACTIVE`/`INACTIVE`, `version`). API: `POST/GET /credit_card` (lista só ACTIVE por padrão; `status` repetível), `GET/PATCH /credit_card/{code}`, `PATCH /credit_card/{code}/inactivate|activate` (idempotentes). A conta padrão de pagamento, quando informada, precisa existir e estar ACTIVE (400). O limite e a conta padrão podem ser trocados, mas ainda não limpos; novos dias de fechamento e vencimento só valerão para compras futuras. Ainda **não há compra, fatura nem limite usado** (E9b). A lógica pura das faturas está em `CardInvoiceCalculator` (sem Spring): fatura do mês M fecha no `closing_day` (mês curto usa o último dia); compra antes do fechamento cai na fatura do mês, no dia do fechamento ou depois cai na do mês seguinte; vencimento no mesmo mês se `due_day > closing_day`, senão no mês seguinte, sem ajuste de dia útil (em mês curto o vencimento ajustado pode coincidir com o fechamento); a parcela k cai k-1 meses depois da primeira e a competência da parcela é o vencimento da fatura. Compra e faturas vieram no E9b (abaixo).
- **E9b — compra no cartão, parcelas e faturas (V22 e V23):** `card_invoice` (uma por cartão e mês de referência, criada quando a primeira parcela cai nela; `closing_date` e `due_date` ficam congeladas) e `card_purchase` (`description` até 100, `purchase_date`, `installments` 1 a 60, `installment_amount` = valor de **cada** parcela com no máximo 8 inteiros e 2 decimais, `category_code` opcional, exclusão lógica, `version`). A V23 liga `financial_movement` à compra, à fatura e ao número da parcela (`card_purchase_id`, `card_invoice_id`, `installment_number`: os três nulos ou os três preenchidos) e alarga `description` para 120. **Cada parcela é uma `FinancialMovement` DEBIT** com `due_date` e `competence_date` iguais ao vencimento da fatura em que cai (competência marcada como manual para nunca ser recalculada) e `movement_date` = data da compra; assim ciclos, relatórios, projeção e atraso funcionam sem lógica nova, e a compra nunca conta em dobro (o pagamento da fatura é que move o caixa, E9c). API: `POST /card_purchase` (201; cartão ACTIVE, categoria DEBIT, 400 nos campos inválidos), `GET /card_purchase?start_date&end_date[&card_code]` (período inclusivo de até 366 dias, sem paginação), `GET/PATCH/DELETE /card_purchase/{code}` (DELETE lógico: 204), `GET /credit_card/{code}/limit` (limite, usado = parcelas ainda não pagas do cartão, futuras inclusive, e disponível), `GET /credit_card/{code}/invoice[?reference_month=yyyy-MM]` (resumos, mais nova primeiro) e `GET /card_invoice/{code}` (com as parcelas). Estado da fatura derivado: `OPEN` antes do fechamento, `CLOSED` depois, `PAID` quando todas as parcelas estão pagas. **409:** total acima do limite disponível (nada é gravado); parcela que cairia numa fatura paga; competência em ciclo fechado; editar data, parcelas ou valor (ou excluir) uma compra que já tem parcela paga. Na edição o valor atual da própria compra libera o limite antes de conferir o novo total; parcelas a mais são excluídas logicamente e as que faltam são criadas. **Ordem de bloqueio:** guarda de ciclo (`PayCycleMovementGuard.lock()`), depois a linha do cartão (`PESSIMISTIC_WRITE`), depois o ciclo fechado de cada competência. `PATCH` e `DELETE /financial_movement/{code}` numa parcela de cartão dão 409: quem altera é a compra. Ainda faltam (E9c/E9d): pagamento e estorno da fatura, bloquear a liquidação avulsa de parcela, mostrar o código da compra e da fatura em `GET /financial_movement`, relatórios e `FixedBill` no cartão.
- **E9c — pagamento e estorno da fatura (V24; próxima migration livre: V25):** `card_invoice_payment` (um pagamento por fatura, registro nunca apagado: `reversed_at`; `UNIQUE (card_invoice_id, active_slot)` garante um único pagamento ativo) e `movement_settlement.invoice_payment_id`. `POST /card_invoice/{code}/payment` (`account_code` opcional, `payment_date`; 201) liquida **o que ainda falta** em cada parcela da fatura, na mesma conta e data: o caixa se move uma vez, na data do pagamento, e nenhum consumo novo nasce. Conta: a informada, senão a conta padrão do cartão, senão a conta padrão; deve estar ACTIVE, e a data do pagamento não pode ser anterior ao saldo inicial da conta (400). `GET /card_invoice/{code}/payment` lista o histórico (mais novo primeiro, estornados incluídos) e `POST /card_invoice/{code}/payment/{paymentCode}/reversal` estorna todas as liquidações do pagamento (parcelas voltam a PENDING/LATE e o limite é liberado). **409:** fatura já paga; fatura sem parcelas ou sem valor a pagar; estorno repetido. **Ordem de bloqueio:** guarda de ciclo, depois a linha do cartão, depois as parcelas da fatura. `POST /financial_movement/{code}/settlements` numa parcela de cartão dá 409 (quem paga é a fatura) e estornar sozinha uma liquidação gerada por pagamento de fatura também dá 409 (estorne o pagamento). Pagar em ciclo fechado é permitido, como qualquer liquidação.
- **E9d — integrações do cartão (sem migration):** `GET /financial_movement` e `GET /financial_movement/{code}` mostram `card_purchase_code`, `card_invoice_code` e `installment_number` (nulos fora de parcela de cartão). Os códigos são resolvidos no `FinancialMovementService` com uma consulta por lote (`findCodesByIdIn`), só quando a lista tem parcelas; o mapper leva apenas o número da parcela e nunca escreve o vínculo de volta (a entidade guarda ids simples, sem associação JPA). Teste de integração `CardCashFlowIntegrationTest`: a compra é uma despesa pendente no vencimento da fatura, o pagamento a move para a data do pagamento como despesa real (uma vez só) e o estorno a devolve ao vencimento. **Decisões que ficam como estão:** conta fixa no cartão e parcelada antiga no cartão continuam fora (padrão adotado na decisão 7; a `InstallmentBill` segue para carnê e financiamento simples).
- **E10 — envelopes por ciclo (V25; próxima migration livre: V26):** `budget_envelope` liga uma categoria DEBIT a um ciclo (`pay_cycle_id`, pelo id: o `code` do ciclo não muda quando as datas se movem) com `planned_amount`; um envelope ativo por categoria e ciclo (coluna gerada `active_slot` + `UNIQUE`), exclusão lógica, `@Version`. API: `POST /pay_cycle/{cycleCode}/envelope` (201), `GET /pay_cycle/{cycleCode}/envelope` (envelopes com totais e `projection_incomplete`), `PATCH /envelope/{code}` e `DELETE /envelope/{code}` (204, idempotente). O consumo vem do resumo do ciclo (E5), então envelope e relatório nunca divergem: `recognized_amount` = pago, `committed_amount` = ainda a pagar (pendente, atrasado, parcelas de cartão), `consumed_amount` = soma, `remaining_amount` = planejado − consumido (negativo se estourou), `reserved_amount` = o que ainda está livre no envelope (nunca negativo; reserva não é saída bancária datada) e `overrun_amount` = estouro. 400: valor inválido ou categoria inexistente, inativa ou não DEBIT. 409: categoria já com envelope ativo no ciclo; ciclo `CLOSED` ou `CANCELLED` (não aceita criar, alterar nem excluir).
- **E11 — previsão de caixa (sem migration; próxima migration livre: V26):** `GET /forecast?until=yyyy-MM-dd&include_late_income=false` (somente leitura, `REPEATABLE_READ`). Horizonte padrão: último dia do ciclo atual, senão fim do mês; 400 se `until` for anterior a hoje ou estiver a mais de 366 dias. Componentes separados e sem dupla contagem: `cash_balance` (saldo real das contas hoje), `expected_income` (receitas a receber no horizonte), `late_income` (receitas atrasadas, **fora** de `projected_cash` a menos que `include_late_income=true`), `obligations` (despesas abertas até o horizonte, atrasadas incluídas, exceto parcelas de cartão), `card_invoices` (parcelas de cartão com vencimento até o horizonte), `projected_cash`, `envelope_reserve` (reserva dos envelopes do ciclo atual, E10), `card_beyond_horizon` (parcelas de cartão após o horizonte) e `free_after_reserves`. Série diária: `lowest_projected_cash`, `lowest_projected_cash_date` e `first_negative_date`. Alertas independentes: `negative_cash_alert` (algum dia abaixo de zero) e `insufficient_budget_alert` (`free_after_reserves` < 0); `projection_incomplete` repete o aviso do resumo do ciclo.
- Fora do escopo por enquanto: multiusuário (`owner_user_id`), compras e faturas de cartão (E9b a E9d) e envelopes (E10). O Dashboard mensal não é adaptado para ciclos.

## O que não fazer

- Não expor entidades JPA na API nem fazer o controller falar com repositório.
- Não editar migrations já aplicadas, nem usar DELETE físico.
- Não trocar caixa por competência nos relatórios existentes.
- Não criar `owner_user_id` nem login sem pedido explícito.
- Não comitar segredos, nem afirmar que testes passaram sem tê-los rodado.
