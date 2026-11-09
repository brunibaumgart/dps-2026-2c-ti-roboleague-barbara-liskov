# 03 — Adaptador GraphQL: dependencias, resolvers, errores, lote y tests

Estado: **implementada**: fase 0 en el PR #23; fases 2, 3 y 4 en los PRs #29, #30, #32, #33 y #35. Fases 0, 2, 3 y 4 del README. Todo en `roboleague-api`.

## 1. Fase 0: dependencias y esqueleto

### Maven (`roboleague-api/pom.xml`)

Las versiones las fija el BOM `spring-boot-dependencies` 4.1.1 que ya importa
el `pom.xml` raíz (Spring for GraphQL 2.0.5, graphql-java 25.0). Sin `<version>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-graphql</artifactId>
</dependency>
<!-- GraphQL tests: HttpGraphQlTester sobre MockMvc, en el mismo contexto que los tests REST. -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-graphql-test</artifactId>
    <scope>test</scope>
</dependency>
```

Los dos artefactos existen en el BOM (comprobado en
`~/.m2/repository/org/springframework/boot/spring-boot-dependencies/4.1.1/`).
`spring-boot-starter-webmvc` ya está: GraphQL se monta sobre MVC en `/graphql`.

### Paquetes de Boot 4 (verificar, no asumir)

Boot 4 movió las auto-configuraciones de test a paquetes por módulo (ejemplo
ya en el repo: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`).
Antes de escribir imports, listar el jar:

```bash
unzip -l ~/.m2/repository/org/springframework/boot/spring-boot-graphql-test/4.1.1/spring-boot-graphql-test-4.1.1.jar | grep -i AutoConfigureHttpGraphQlTester
```

y usar el paquete que aparezca (esperado:
`org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester`).
Lo mismo para cualquier clase de `spring-boot-graphql` si hiciera falta. Las
clases de Spring for GraphQL no cambiaron: `org.springframework.graphql.data.method.annotation.{QueryMapping, SchemaMapping, BatchMapping, Argument}`,
`org.springframework.graphql.execution.{DataFetcherExceptionResolverAdapter, ErrorType}`,
`org.springframework.graphql.test.tester.HttpGraphQlTester`.

### Configuración (`application.yml`)

```yaml
spring:
  graphql:
    graphiql:
      enabled: true        # http://localhost:8080/graphiql, para la demo
```

El endpoint queda en `/graphql` (default). No habilitar CORS ni WebSocket.

### `ApiTest` (test base compartida)

Agregar, sin crear otra clase base (un solo contexto Spring y un solo Postgres
para toda la suite de API). Decisión explícita: el tester va en `ApiTest` y no
en una subclase `GraphQlApiTest`, porque una anotación distinta en la subclase
cambia la configuración del contexto y Spring levanta un **segundo** contexto
(y potencialmente otro Postgres) para los tests GraphQL. El costo es que los
tests REST cargan un bean más; aceptado.

Cliente: `@AutoConfigureHttpGraphQlTester` en un `@SpringBootTest` con entorno
mock construye el `HttpGraphQlTester` sobre un `WebTestClient` atado a MockMvc
(`MockMvcWebTestClient`), sin servidor real. `WebTestClient` vive en
`spring-webflux`, que `spring-boot-starter-graphql-test` tiene que arrastrar;
si en Boot 4 no lo hace (verificar con `mvn dependency:tree -pl roboleague-api`
después de agregar la dependencia), agregar `org.springframework:spring-webflux`
con `scope` test. Plan B sin ninguna dependencia nueva: `mvc.perform(post("/graphql")
.contentType(APPLICATION_JSON).content("{\"query\": \"…\"}"))` con `jsonPath`,
igual que los tests REST; menos cómodo pero suficiente para toda la suite.

```java
@SpringBootTest(properties = {
        // Evidencia del N+1: cuenta sentencias preparadas por sesión (solo en tests).
        "spring.jpa.properties.hibernate.generate_statistics=true",
        // Hibernate loguea métricas por sesión en INFO cuando las estadísticas están activas.
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"})
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
@Import({PostgresContainer.class, ApiTest.FixedClock.class})
public abstract class ApiTest {
    @Autowired protected MockMvc mvc;
    @Autowired protected HttpGraphQlTester graphQl;
}
```

Comprobar después del cambio que `TransactionalUseCasesTest` y
`ApiExceptionHandlerTest` siguen verdes (comparten contexto).

### Esqueleto

- `src/main/resources/graphql/schema.graphqls` con `Query.editions` y `Edition`
  (solo escalares) de la spec 01.
- `com.roboleague.api.graphql.EditionQueries` con `@QueryMapping editions()`.
- `GraphQlSmokeTest extends ApiTest`: `graphQl.document("{ editions { id } }").execute().path("editions").entityList(Object.class)`.
- Arrancar la app (o leer el log del test): Spring for GraphQL imprime un
  informe de inspección del esquema ("GraphQL schema inspection"). Debe decir
  `Unmapped fields: {}`. Ese informe es el control de la fase 2 también.

Resultado de la fase 0 (2026-11-06): `spring-webflux` llega en scope test con
`spring-boot-starter-graphql-test`, así que el tester funciona sin dependencia
extra. El informe de inspección dice `Unmapped fields: {}`. En el navegador,
`/graphiql` carga, el panel de docs lista el esquema y ejecuta consultas; el
autocompletado del editor queda en «Loading...» porque el bundle de
`@graphiql/react` 0.37.7 que trae Boot no resuelve `@emotion/is-prop-valid` en
`esm.sh` (error en consola). No es nuestro código: queda anotado en DESIGN
2.33 y en la deuda; la demo se apoya en docs + ejecución y en los `curl`.

## 2. Fase 2: estructura del adaptador

```
com.roboleague.api.graphql
  GraphQlConfig                 @Bean Instrumentation maxDepth (D8)
  GraphQlErrorResolver          DataFetcherExceptionResolverAdapter (D6)
  EditionQueries                Query.editions/edition, Edition.challenges/registrations, Registration.category
  ChallengeQueries              Query.challenge, Challenge.edition/rounds/standings/appeals/rulebook(version)
  TeamQueries                   Query.team, Team.results, y TODOS los @BatchMapping de `team`
  RoundQueries                  Query.round/slot, Round.challenge/category, Slot.turn (lote), Attempt.round y RoundResult.round (lote)
  AttemptQueries                Query.attempt, Attempt.breakdown/appeals, RoundResult.attempt/round, Appeal.attempt
  StandingsQueries              Query.standings
  view/                         EditionView, TournamentView, SeasonView, CategoryView, RegistrationView, TeamView,
                                MemberView, RobotView, DocumentationView, DocumentView, TeamTurnView, ChallengeView,
                                RulebookView, RoundView, SlotView, TrackView, JudgeView, CancellationView,
                                AttemptView, ScoreBreakdownView, ScoreItemView, SourceContributionView, RevisionView,
                                StandingsView, StandingsVersionSummaryView, StandingsVersionView, PublicationView,
                                PendingView, StandingsEntryView, RoundResultView, AppealView
```

Convenciones:

- Clases `@Controller` package-private, constructor con los casos de uso
  (como los `@RestController`). Nombre `XxxQueries`, no `XxxController`, para
  distinguirlos de REST en el árbol.
- Regla #2 de la cursada (3 parámetros por constructor): aplica al modelo, no
  a estos adaptadores. Los `*View` son vistas planas con tantos campos como el
  esquema declare, con la misma exención que los DTO REST (DESIGN 2.15); los
  resolvers reciben casos de uso (puertos, no datos), como los controllers.
  Un `View` que empieza a tener comportamiento es una señal de que falta un
  tipo en application, no de que haya que partirlo.
- `@QueryMapping` para los campos de `Query`; `@SchemaMapping(typeName = "Challenge", field = "edition")`
  para relaciones; argumentos con `@Argument`. Convertir texto a ids tipados en
  el resolver (`EditionId.of(id)`), nunca en el view.
- Un resolver **nunca** importa `com.roboleague.repository.*`. Criterio 2 del README:
  `grep -rn "Repository" roboleague-api/src/main/java/com/roboleague/api/graphql` vacío.
- `Slot.turn` es un solo `@BatchMapping` que agrupa los `SlotView` por `roundId`
  y llama a `QueryTurnAttemptsUseCase.ofRounds` una sola vez con todas las rondas; devuelve un
  `TurnView` con los cuatro campos. Por eso el SDL agrupa esos campos en `Turn`
  en vez de dejarlos sueltos en `Slot` (D12): cuatro campos sueltos serían
  cuatro resolvers (o cuatro lotes) sobre el mismo caso de uso.
- El límite de profundidad (D8): `@Bean Instrumentation` →
  `new graphql.analysis.MaxQueryDepthInstrumentation(10)`. Spring for GraphQL
  registra todo bean `Instrumentation`. **La introspección no cuenta**: la
  `IntrospectionQuery` de GraphiQL supera 10 niveles (`__schema → types →
  fields → type → ofType × 7`). Envolver la instrumentación en una subclase o
  en un `ChainedInstrumentation` que devuelva `SimpleInstrumentationContext`
  sin validar cuando todos los campos raíz de la operación empiezan con `__`
  (ver `graphql.analysis.MaxQueryDepthInstrumentation#beginValidation` y
  `QueryTraverser`; si resulta más simple, `MaxQueryDepthInstrumentation(10,
  info -> !isIntrospection(info))` con la sobrecarga que recibe el predicado,
  verificar que exista en graphql-java 25). Comprobación de la fase 0: abrir
  `/graphiql`, el panel de docs carga y el autocompletado funciona; agregar
  `GraphQlDepthLimitTest` un caso que ejecuta la introspección por `/graphql`
  y no recibe error.

## 3. Errores (D6)

```java
@Component
class GraphQlErrorResolver extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        return switch (ex) {
            case NotFoundException e -> error(env, ErrorType.NOT_FOUND, e.getMessage());
            case IllegalArgumentException e -> error(env, ErrorType.BAD_REQUEST, e.getMessage());
            // Base caída: REST responde 503 con UNAVAILABLE; ErrorType no tiene SERVICE_UNAVAILABLE.
            case DataAccessResourceFailureException e -> unavailable(env, e);
            case CannotCreateTransactionException e -> unavailable(env, e);
            // IllegalStateException en una lectura (p. ej. una categoría puntuada con dos versiones) es un
            // estado guardado inconsistente, no una transición del pedido: cae en default. ErrorType no tiene CONFLICT.
            default -> { log.error("Unexpected failure while resolving " + env.getField().getName(), ex);
                         yield error(env, ErrorType.INTERNAL_ERROR, ApiExceptionHandler.INTERNAL_ERROR); }
        };
    }
}
```

- `error(env, type, message)` = `GraphqlErrorBuilder.newError(env).errorType(type).message(message).build()`;
  así el error trae `path` y `locations` del campo que falló.
- `unavailable(env, e)`: `log.warn("Database unavailable", e)` y
  `error(env, ErrorType.INTERNAL_ERROR, ApiExceptionHandler.UNAVAILABLE)`.
- `InconsistentStateException`, `CorruptDataException`, `IllegalStateException`
  y cualquier otra caen en `default`: mensaje genérico, causa al log (mismo
  criterio que `ApiExceptionHandler.internal`). Reusar las constantes
  `INTERNAL_ERROR` y `UNAVAILABLE` (hacerlas accesibles al paquete graphql;
  preferir eso a duplicar el texto).
- Tabla completa en el README (D6). Es deliberadamente más corta que la de
  REST: 409 y 422 son de escrituras.
- Si `ApiExceptionHandler` resulta cómodo de reutilizar, **no** hacerlo: traduce
  a `ResponseEntity`, otro contrato. Dos traductores pequeños con la misma
  tabla es la solución; anotar en `DESIGN.md` 2.33 la duplicación consciente.
- La validación del documento (campo inexistente, argumento faltante, tipo
  incorrecto) la hace graphql-java antes de ejecutar: no hay nada que escribir,
  solo testearlo.

## 4. Views

Ver reglas en la spec 01. Detalles de conversión, copiados de los DTO REST para
que REST y GraphQL muestren lo mismo:

| Campo | Conversión |
| --- | --- |
| ids | `XxxId.value()` |
| fechas | `toString()` de `LocalDate`/`LocalDateTime` |
| `Points` | `doubleValue()` |
| `Attempt.score` | `attempt.countableScore().map(ScoreBreakdown::totalScore).map(Points::doubleValue).orElse(null)` |
| `ScoreBreakdown` | mismos campos que `BreakdownDto`: `awaiting` ordenado, `items` de `getScoreBreakdown().items()`, `notesAndPenalties()`, `bySource` de `AttemptBreakdown.bySource()`, `revisions` de `getRevisionHistory()` |
| `StandingsEntry` | como `StandingsVersionDto.EntryDto` más `teamName = entry.team().teamName()` |
| `StandingsStatus` | `standings.statusOf(version).name()` |
| `Rulebook.selectionRule` | `rulebook.rankingScheme().roundSelection().describe()`; `attemptRule` ídem con `attemptSelection()`; `tieBreakCriteria` de `rulebook.definition().ranking().criteria()` |
| `Documentation.documents` | `getDocuments()` (`Map<String,String>`) → lista de `DocumentView(kind, reference)` ordenada por `kind` |

## 5. Fase 3: lotes y evidencia (D5)

Cinco lotes, todos con la misma forma. `team` es el que se mide antes y
después y el que se muestra en la presentación; los otros se implementan
directamente en lote y cada uno tiene su aserción en `GraphQlBatchingTest`.

| Campo(s) | Clave de agrupación | Caso de uso | Lecturas por nivel |
| --- | --- | --- | --- |
| `StandingsEntry.team`, `Registration.team`, `Slot.team`, `Attempt.team`, `Appeal.team` | `teamId` | `QueryTeamsUseCase.byIds` | 1 |
| `RoundResult.attempt`, `Appeal.attempt` | `attemptId` | `GetAttemptBreakdownUseCase.byIds` | 2 |
| `Attempt.breakdown` (cuando el view no lo trae) | `id` | `GetAttemptBreakdownUseCase.byIds` | 2 |
| `RoundResult.round`, `Attempt.round` | `roundId` | `QueryRoundsUseCase.byIds` | 1 |
| `Round.challenge` | `challengeId` | `GetChallengeUseCase.byIds` | 1 |
| `Challenge.edition`, `Round.category` | `editionId` | `QueryEditionsUseCase.byIds` | 1 |
| `Slot.turn` | `roundId` | `QueryTurnAttemptsUseCase.ofRounds` | 4 por (desafío, categoría) |

### Primero medir el problema (solo `team`)

Implementar `StandingsEntry.team` por fila (`@SchemaMapping` → `queryTeams.get`)
y escribir el test de abajo con la aserción invertida (o sin aserción,
imprimiendo el conteo). Anotar el número: con 3 equipos en la tabla se esperan
**1 + 3** sentencias para `team` además de las de la tabla. Guardar ese valor
para `docs/graphql-vs-rest.md` §"Evidencia N+1". Para `attempt`, `round` y
`turn` no hace falta medir la versión por fila: el razonamiento es el mismo y
el test de abajo prueba la versión en lote.

### Después tratarlo

```java
@BatchMapping(typeName = "StandingsEntry", field = "team")
Map<StandingsEntryView, TeamView> standingsTeams(List<StandingsEntryView> entries) {
    Map<TeamId, Team> teams = queryTeams.byIds(entries.stream().map(e -> TeamId.of(e.teamId())).toList());
    return entries.stream().collect(toMap(identity(), e -> TeamView.from(teams.get(TeamId.of(e.teamId())))));
}
```

Un método igual por tipo (`Registration`, `Slot`, `Attempt`, `Appeal`), los
cinco en `TeamQueries` llamando a `queryTeams.byIds`. `@BatchMapping` registra
un `DataLoader` por campo: graphql-java junta las claves de todas las filas del
mismo nivel y llama una vez. Los views son records: sirven de clave de mapa
por igualdad de valor.

Los otros lotes, en la clase del tipo que devuelven:
- `AttemptQueries`: `RoundResult.attempt` y `Appeal.attempt` → `breakdowns.byIds`,
  construyendo `AttemptView.from(attemptBreakdown)` (con breakdown incluido);
  `Attempt.breakdown` → partir la lista en views que ya traen breakdown (se
  devuelven tal cual) y los que no (`breakdowns.byIds` con sus ids).
- `RoundQueries`: `RoundResult.round` y `Attempt.round` → `queryRounds.byIds`;
  `Slot.turn` → `queryTurns.ofRounds(roundIds distintos de los SlotView)` una
  sola vez, `TurnView.from(turnResults)` por slot; `Round.challenge` →
  `getChallenge.byIds`; `Round.category` → `queryEditions.byIds` y
  `edition.category(categoryId)`.
- `ChallengeQueries`: `Challenge.edition` → `queryEditions.byIds`.
- Dos views con el mismo contenido (dos filas de la tabla con el mismo `roundId`)
  son la misma clave de mapa: devolver el mapa con `toMap(identity(), …, (a, b) -> a)`
  o construirlo con `LinkedHashMap` para tolerar duplicados.

### Test de evidencia: `GraphQlBatchingTest extends ApiTest`

- Escenario propio: edición `ed-gql-batch`, categoría, **tres** equipos
  inscriptos, un desafío con una ronda y un turno por equipo, un resultado por
  turno, `POST .../standings/versions` (copiar helpers de `StandingsControllerTest`).
- Estadísticas: `@Autowired EntityManagerFactory emf;`
  `Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();`
  Requiere `hibernate.generate_statistics=true` en las propiedades de `ApiTest`
  (§1). El contexto y las estadísticas son **compartidos por toda la suite**:
  `stats.clear()` inmediatamente antes de cada consulta medida y leer el
  contador inmediatamente después, sin otra llamada en el medio; surefire
  corre los tests en un hilo, así que no hay otra sesión sumando.
- Medir `stats.clear(); ejecutar consulta; stats.getPrepareStatementCount()` para:
  1. `standings(...) { latest { entries { position teamName } } }` → `base`;
  2. `standings(...) { latest { entries { position team { institution } } } }` → `withTeams`.
- Aserción: `withTeams - base == 1` (una sola sentencia para los tres equipos).
  Con N+1 sería 3. Escribir en el nombre del test lo que prueba:
  `resolvingTheTeamOfEveryEntryCostsOneStatementNotOnePerEntry`.
- Mismo esquema para los otros lotes, cada uno su test en la misma clase, con
  la diferencia esperada escrita como constante nombrada y explicada:
  - `considered { attempt { score } }` sobre las tres filas: `withAttempts - base`
    == 1 (intentos) + 1 (desafíos) = **2**; con N+1 sería 6.
  - `considered { round { number } }`: diferencia == **1**; con N+1 sería 3.
  - `considered { round { challenge { name } } }`: diferencia == 1 + **1**.
  - `considered { round { category { name } } }`: diferencia == 1 + **1**.
  - `round(id) { slots { turn { acceptsResult attempts { id } } } }` con tres
    slots de **una** ronda: diferencia == **4** (la constante de `ofRounds`);
    con N+1 sería 12. Es la medición que define "fila" para `turn`: slots de
    una ronda, no rondas de un desafío.
  - `considered { attempt { breakdown { total } } }`: igual que `attempt` solo,
    porque el view ya trae el breakdown: diferencia == **2**, no 2 + 3.
  - Escribir cada constante con nombre (`ONE_STATEMENT_FOR_ALL_TEAMS`…) y un
    comentario de una línea que diga qué lecturas son.
- Si `getPrepareStatementCount` incluye sentencias de Flyway/validación en el
  primer uso, calentar con una consulta previa antes de `clear()`.

## 6. Fase 4: suite GraphQL

Todos en `roboleague-api/src/test/java/com/roboleague/api/graphql/`, extienden
`ApiTest`, ids con prefijo `-gql-`. Escenario compartido en una clase de soporte
`GraphQlScenario` (edición, categoría, equipos, desafío, rondas, resultados vía
casos de uso/REST, como hace `StandingsControllerTest`), para no repetir 80
líneas por test.

| Test | Qué verifica |
| --- | --- |
| `EditionQueriesTest` | `editions` lista la edición del escenario; `edition(id)` con `challenges { id name currentRulebook { version requiredSources } }` y `registrations { team { name } }`; `registrations(categoryId: ajena)` → error `BAD_REQUEST` |
| `TeamResultsQueryTest` | **la consulta de la consigna**: `team(id) { name results(challengeId) { round { number } attempts { id score breakdown { total items { concept subtotal } bySource { source subtotal } } } } }`; rondas en orden; un turno sin resultado trae `attempts: []`; un intento que espera fuente trae `score: null` y `breakdown.awaiting` no vacío |
| `StandingsQueryTest` | `standings { latest { rulebookVersion entries { position explanation teamName team { institution } considered { attempt { score } } discarded { roundId } } } official pending { outdated } }` sobre un desafío mejores 1 de 2 (copiar el caso de `StandingsControllerTest`) |
| `GraphQlBatchingTest` | §5 |
| `GraphQlErrorsTest` | (a) `edition(id: "ed-gql-missing") { name }` → `path("edition").valueIsNull()` y `errors().expect(e -> e.getErrorType() == ErrorType.NOT_FOUND && e.getMessage().equals("Edition not found: ed-gql-missing"))`; (b) `{ editions { nombre } }` (campo inexistente) → `errors().satisfy(list -> …)` con un error cuya clasificación sea `ValidationError` y mensaje que nombre `nombre`, y `data` ausente; (c) `attempt(id: "sin-numero")` → `BAD_REQUEST` (`AttemptId.parse`); (d) `standings(challengeId, categoryId: "cat-gql-otra")` → `BAD_REQUEST` con el mensaje de `Edition.category` |
| `GraphQlDepthLimitTest` | una consulta de profundidad 11 (`challenge { edition { challenges { edition { … }}}}`) devuelve un error de graphql-java por profundidad y ninguna data |
| `GraphiQlTest` (en `WebFrontendTest` o aparte) | `GET /graphiql` responde 200 con HTML |

Con `HttpGraphQlTester`: `graphQl.document(doc).variable("id", x).execute()`;
`.path("team.name").entity(String.class).isEqualTo("Alpha")`;
`.path("team.results").entityList(Map.class).hasSize(2)`; para errores
`.execute().errors().expect(...).verify()` o `.errors().satisfy(...)`. Un
`execute()` con errores no esperados falla solo: en los tests de error hay
que consumirlos con `errors()` **antes** de leer `path`.

`DemoFixtureTest`: agregar una aserción GraphQL mínima sobre la demo cargada
(`team(id: "t-b") { results(challengeId: "ch-maze") { attempts { id score } } }`
devuelve `demo-7-1` con `257.5`) solo si ese test ya dispone de `HttpGraphQlTester`
en su contexto; si usa otro contexto, no duplicarlo.

## Verificación de las fases 2–4

```bash
mvn -B -pl roboleague-api -am test        # Docker
mvn -B verify                             # cierre: CI completa, cobertura, tests JS aparte con node
node --test roboleague-api/src/test/frontend/*.test.mjs
```

Antes del PR, correr la app con `--spring.profiles.active=demo` sobre la base
de `docker compose`, abrir `/graphiql` y ejecutar las tres consultas del README
(spec 04). Si alguna falla, la demo de la presentación falla.
