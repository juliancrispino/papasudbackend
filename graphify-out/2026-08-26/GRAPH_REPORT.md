# Graph Report - papasudbackend  (2026-08-26)

## Corpus Check
- 161 files · ~40,143 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 994 nodes · 3589 edges · 34 communities (29 shown, 5 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 330 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `ffa31508`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- org.junit.jupiter.api.Test
- lombok.RequiredArgsConstructor
- lombok.Builder
- IaService
- StockOverviewProjection
- org.springframework.stereotype.Component
- ApiException
- StockIntakeService
- org.springframework.stereotype.Repository
- DtoMapper
- GroqStructuredClient
- V2__papastock_stock_model.sql
- MovementItem
- StockIntakeService.java
- HeuristicMovementParser
- Lot
- org.springframework.transaction.annotation.Transactional
- CatalogResolver
- TraceabilityService
- ScryptPasswordHasher
- mvnw
- OperationsContextService
- StockMovement
- CSV de importación — `Planilla de movimientos 2026.xls`
- TipoMovimiento
- PapasudApplication
- MovementNumbers
- HashPasswordTool
- TipoUbicacion
- V1__baseline_legacy_schema.sql
- com.hackaton:papasud

## God Nodes (most connected - your core abstractions)
1. `Lot` - 50 edges
2. `Location` - 48 edges
3. `StockOverviewProjection` - 43 edges
4. `StockMovement` - 42 edges
5. `IaService` - 41 edges
6. `DtoMapper` - 34 edges
7. `ApiResponse` - 34 edges
8. `StockIntakeService` - 33 edges
9. `ApiIntegrationTest` - 32 edges
10. `TestDataSeeder` - 30 edges

## Surprising Connections (you probably didn't know these)
- `AiController` --references--> `OperationsContextService`  [EXTRACTED]
  src/main/java/com/hackaton/papasud/api/controller/AiController.java → src/main/java/com/hackaton/papasud/api/service/OperationsContextService.java
- `AiController` --references--> `IaService`  [EXTRACTED]
  src/main/java/com/hackaton/papasud/api/controller/AiController.java → src/main/java/com/hackaton/papasud/ia/service/IaService.java
- `AuthController` --references--> `SessionService`  [EXTRACTED]
  src/main/java/com/hackaton/papasud/api/controller/AuthController.java → src/main/java/com/hackaton/papasud/auth/SessionService.java
- `ImportController` --references--> `PlanillaImportService`  [EXTRACTED]
  src/main/java/com/hackaton/papasud/api/controller/ImportController.java → src/main/java/com/hackaton/papasud/api/service/PlanillaImportService.java
- `ImportController` --references--> `StockIntakeService`  [EXTRACTED]
  src/main/java/com/hackaton/papasud/api/controller/ImportController.java → src/main/java/com/hackaton/papasud/api/service/StockIntakeService.java

## Import Cycles
- None detected.

## Communities (34 total, 5 thin omitted)

### Community 0 - "org.junit.jupiter.api.Test"
Cohesion: 0.06
Nodes (27): io.zonky.test.db.postgres.embedded.EmbeddedPostgres, jakarta.servlet.http.Cookie, javax.sql.DataSource, org.junit.jupiter.api.DisplayName, org.junit.jupiter.api.Test, org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.params.provider.ValueSource, org.springframework.boot.test.context.SpringBootTest (+19 more)

### Community 1 - "lombok.RequiredArgsConstructor"
Cohesion: 0.07
Nodes (35): lombok.RequiredArgsConstructor, org.springframework.http.ResponseEntity, org.springframework.security.access.prepost.PreAuthorize, org.springframework.web.bind.annotation.DeleteMapping, org.springframework.web.bind.annotation.GetMapping, org.springframework.web.bind.annotation.PatchMapping, org.springframework.web.bind.annotation.PostMapping, org.springframework.web.bind.annotation.RequestMapping (+27 more)

### Community 2 - "lombok.Builder"
Cohesion: 0.06
Nodes (55): jakarta.persistence.Entity, jakarta.persistence.Table, lombok.AllArgsConstructor, lombok.Builder, lombok.Getter, lombok.NoArgsConstructor, lombok.Setter, CorrectionResultDto (+47 more)

### Community 3 - "IaService"
Cohesion: 0.06
Nodes (26): org.junit.jupiter.api.BeforeEach, org.junit.jupiter.api.extension.ExtendWith, org.mockito.junit.jupiter.MockitoExtension, DiscrepancyAnalysisDto, Evidence, Hypothesis, DiscrepancyRequestDto, StockDto (+18 more)

### Community 4 - "StockOverviewProjection"
Cohesion: 0.09
Nodes (10): MovementIntentDto, MovementIntentItemDto, MovementInterpretationDto, ValidationErrorDto, StockTransferService, PlannedLine, TransferPlan, TransferPlanner (+2 more)

### Community 5 - "org.springframework.stereotype.Component"
Cohesion: 0.08
Nodes (27): jakarta.servlet.FilterChain, jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse, java.net.URI, org.springframework.boot.context.properties.ConfigurationProperties, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration, org.springframework.http.ResponseCookie (+19 more)

### Community 6 - "ApiException"
Cohesion: 0.09
Nodes (23): java.time.format.DateTimeParseException, org.springframework.dao.DataIntegrityViolationException, org.springframework.dao.OptimisticLockingFailureException, org.springframework.dao.PessimisticLockingFailureException, org.springframework.http.converter.HttpMessageNotReadableException, org.springframework.http.HttpHeaders, org.springframework.http.HttpStatus, org.springframework.http.HttpStatusCode (+15 more)

### Community 7 - "StockIntakeService"
Cohesion: 0.09
Nodes (11): lombok.extern.slf4j.Slf4j, org.apache.poi.ss.usermodel.Cell, PlanillaImportIssueDto, NewLocation, NewLot, PlanillaImportPreviewDto, PlanillaImportRowDto, PlanillaSheetSummaryDto (+3 more)

### Community 8 - "org.springframework.stereotype.Repository"
Cohesion: 0.11
Nodes (16): org.springframework.data.jpa.repository.JpaRepository, org.springframework.data.jpa.repository.Lock, org.springframework.data.jpa.repository.Modifying, org.springframework.data.jpa.repository.Query, org.springframework.stereotype.Repository, SnapshotService, AuthSessionRepository, IdempotencyRecordRepository (+8 more)

### Community 9 - "DtoMapper"
Cohesion: 0.10
Nodes (5): CorrectionRequestDto, StockCountRequestDto, DtoMapper, ApiDates, tools.jackson.core.type.TypeReference

### Community 10 - "GroqStructuredClient"
Cohesion: 0.10
Nodes (16): com.fasterxml.jackson.annotation.JsonIgnoreProperties, com.fasterxml.jackson.annotation.JsonInclude, org.springframework.test.web.client.MockRestServiceServer, org.springframework.web.client.HttpStatusCodeException, org.springframework.web.client.RestTemplate, GroqStructuredClient, SchemaRejectedException, Message (+8 more)

### Community 11 - "V2__papastock_stock_model.sql"
Cohesion: 0.13
Nodes (32): customers, data_imports, export_operations, export_requirement_fields, export_requirement_sets, generated_documents, import_errors, locations (+24 more)

### Community 12 - "MovementItem"
Cohesion: 0.12
Nodes (10): ReceptionItemDto, ReceptionRequestDto, IdempotencyService, Replay, AdjustmentLine, ItemReception, MovementReceptionService, Entity (+2 more)

### Community 13 - "StockIntakeService.java"
Cohesion: 0.20
Nodes (11): org.springframework.jdbc.core.JdbcTemplate, org.springframework.jdbc.core.RowMapper, org.springframework.stereotype.Service, LotCorrectionService, StockCountService, TextKeys, LocationRepository, LotRepository (+3 more)

### Community 14 - "HeuristicMovementParser"
Cohesion: 0.26
Nodes (6): java.util.regex.Pattern, Catalog, HeuristicMovementParser, LocationHit, QuantitySpan, Span

### Community 15 - "Lot"
Cohesion: 0.35
Nodes (5): Applied, TraceabilityWriter, Location, Lot, TraceabilityEvent

### Community 16 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.21
Nodes (4): jakarta.persistence.EntityManager, org.springframework.transaction.annotation.Transactional, Fixture, TestDataSeeder

### Community 17 - "CatalogResolver"
Cohesion: 0.27
Nodes (6): CatalogResolver, LocationMatch, Outcome, AMBIGUOUS, FOUND, NOT_FOUND

### Community 19 - "ScryptPasswordHasher"
Cohesion: 0.31
Nodes (3): java.security.SecureRandom, Parsed, ScryptPasswordHasher

### Community 20 - "mvnw"
Cohesion: 0.33
Nodes (6): mvnw script, clean(), die(), exec_maven(), set_java_home(), verbose()

### Community 21 - "OperationsContextService"
Cohesion: 0.24
Nodes (3): OperationsContextService, org.springframework.data.jpa.repository.Query, org.springframework.data.jpa.repository.Query

### Community 22 - "StockMovement"
Cohesion: 0.36
Nodes (3): MovementWriter, StockMovement, StockMovementRepository

### Community 23 - "CSV de importación — `Planilla de movimientos 2026.xls`"
Cohesion: 0.29
Nodes (6): Archivo plano de revisión, Configuración en DBeaver, CSV de importación — `Planilla de movimientos 2026.xls`, Cómo se mapeó cada hoja, Limitaciones reales, Orden de importación

### Community 24 - "TipoMovimiento"
Cohesion: 0.40
Nodes (4): TipoMovimiento, AJUSTE, DESPACHO, TRASLADO

### Community 28 - "TipoUbicacion"
Cohesion: 0.50
Nodes (3): TipoUbicacion, FRIGORIFICO, GALPON

### Community 29 - "V1__baseline_legacy_schema.sql"
Cohesion: 1.00
Nodes (3): lotes, movimientos, ubicaciones

## Knowledge Gaps
- **21 isolated node(s):** `com.hackaton:papasud`, `ReceptionItemDto`, `FOUND`, `NOT_FOUND`, `AMBIGUOUS` (+16 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TestDataSeeder` connect `org.springframework.transaction.annotation.Transactional` to `org.junit.jupiter.api.Test`, `lombok.RequiredArgsConstructor`, `org.springframework.stereotype.Component`, `StockIntakeService.java`, `StockMovement`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Why does `IaService` connect `IaService` to `lombok.RequiredArgsConstructor`, `StockOverviewProjection`, `StockIntakeService`, `org.springframework.stereotype.Repository`, `GroqStructuredClient`, `StockIntakeService.java`, `HeuristicMovementParser`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **Why does `ApiIntegrationTest` connect `org.junit.jupiter.api.Test` to `org.springframework.transaction.annotation.Transactional`, `GroqStructuredClient`?**
  _High betweenness centrality (0.043) - this node is a cross-community bridge._
- **What connects `com.hackaton:papasud`, `ReceptionItemDto`, `FOUND` to the rest of the system?**
  _21 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `org.junit.jupiter.api.Test` be split into smaller, more focused modules?**
  _Cohesion score 0.059928582518242506 - nodes in this community are weakly interconnected._
- **Should `lombok.RequiredArgsConstructor` be split into smaller, more focused modules?**
  _Cohesion score 0.0652442250044084 - nodes in this community are weakly interconnected._
- **Should `lombok.Builder` be split into smaller, more focused modules?**
  _Cohesion score 0.06292955326460481 - nodes in this community are weakly interconnected._