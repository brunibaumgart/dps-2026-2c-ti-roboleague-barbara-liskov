# Entrega 3: API GraphQL de consulta sobre la aplicación existente

Estado: **implementada** (PRs #23, #25, #28 a #35); ver "Implementación realizada". Este directorio es el plan completo
para que un agente (o una persona) la implemente de punta a punta. Leer este
README entero antes de abrir las specs numeradas; cada spec dice qué archivos
toca, qué decisiones ya están tomadas y cómo se verifica.

Fuente: consigna "Trabajo Integrador - Entrega 3" de la cátedra (PDF, no se
versiona en este repo). Se contrastó con el código en `main` (commit `9294c05`,
que incluye las correcciones de la Entrega 1) el 5/11/2026.

## Qué pide la consigna (resumen fiel)

> La propuesta de valor es exponer el dominio de consulta mediante una **API
> GraphQL**, conviviendo con la API REST existente.

| Elemento | Requisito textual | Dónde se cumple |
| --- | --- | --- |
| Esquema | Cubre competencias, categorías, equipos, rondas, resultados, ranking y el detalle explicado del puntaje (`ScoreBreakdown`) | spec 01 |
| Consultas anidadas | Equipo, resultados por ronda y desglose del puntaje en **una sola consulta** | spec 01 (`Team.results`), spec 03 |
| Implementación | Resolvers que delegan en los casos de uso existentes; la lógica de negocio no se duplica en la capa GraphQL | spec 02 y 03 |
| Problema N+1 | Identificado y tratado en al menos un punto (carga por lotes o `DataLoader`), **con la evidencia correspondiente** | spec 03 (`@BatchMapping` + test con conteo de sentencias) |
| Comparación con REST | Documento breve que compare ambas APIs sobre **dos casos concretos**: qué pide cada cliente, cuántas peticiones necesita y qué datos de más o de menos recibe | spec 04 (`docs/graphql-vs-rest.md`) |
| Pruebas | De las consultas principales, incluyendo **una de error** (campo inexistente o entidad no encontrada), ejecutables en CI | spec 03 |
| Arquitectura | Conserva la arquitectura elegida; el dominio sigue libre de frameworks; la capa nueva está detrás de contratos propios | decisiones D1–D4 |
| `DESIGN.md` | Patrón/principio aplicado, dónde y por qué; alternativas descartadas; patrones no aplicados con consecuencias | spec 04 |
| Presentación oral | Un integrante elegido al azar presenta todo; demo en vivo con consultas, contraste REST/GraphQL (sobre- y sub-obtención) y decisiones del esquema | spec 05 |
| Extensión opcional | *Subscription* con ranking en vivo | **No se implementa** (D9) |

No se exige rehacer la aplicación ni sumar funcionalidades ajenas a GraphQL.
La "justificación escrita" solo aplica si el equipo sostiene que la propuesta
no aplica al dominio: **no es el caso**, se implementa.

## Decisiones tomadas (no reabrir durante la implementación)

Cada decisión se copia a `DESIGN.md` en la spec 04 con el formato de las
secciones 2.x. Acá va la versión corta, con la alternativa descartada.

| # | Decisión | Alternativa descartada y por qué |
| --- | --- | --- |
| D1 | GraphQL vive en `roboleague-api`, paquete `com.roboleague.api.graphql`, como **segundo adaptador de entrada** junto a REST. Comparte `UseCaseConfig`, `TransactionalUseCases` y los casos de uso. | Módulo Maven `roboleague-graphql` aparte: necesitaría su propia clase de arranque Spring para testearse (la actual vive en api) o depender de api, creando un ciclo; además los controllers REST ya conviven con el front en el mismo módulo. Se gana visibilidad en el POM pero se paga con un contexto Spring de test duplicado. |
| D2 | **Solo consultas** (`type Query`). Las mutaciones siguen en REST. | Exponer mutaciones por GraphQL duplicaría los contratos de escritura (resultados `sealed` → errores por campo) y la consigna pide "exponer el dominio de consulta". |
| D3 | **Schema-first**: el esquema está en `src/main/resources/graphql/schema.graphqls` y Spring for GraphQL lo liga a métodos `@QueryMapping`/`@SchemaMapping`/`@BatchMapping`. | Code-first (generar el esquema desde clases Java): ata el esquema a nombres Java y hace más difícil decidir qué **no** exponer; el esquema como archivo es lo que se defiende en la presentación. |
| D4 | Los resolvers devuelven **records `*View` propios del paquete graphql** (ids como `String`, fechas ISO como `String`, `Points` como `Float`), nunca agregados del dominio. `Points` sale como `Float` **igual que en REST** (`BreakdownDto` usa `Double`); en el modelo sigue exacto (DESIGN 2.28), la pérdida es solo de presentación. Las relaciones (`Challenge.edition`, `StandingsEntry.team`, `Attempt.breakdown`…) **no** se embeben en el record: se resuelven con `@SchemaMapping`/`@BatchMapping` solo si la consulta las pide. No se agrega ninguna interfaz (regla #18): los resolvers reciben los casos de uso concretos. | Dejar que graphql-java lea getters de `Edition`/`Attempt`: expone `EditionId` y `Points` sin conversión, acopla el esquema a nombres internos y rompe la regla "no serializar agregados". Reusar los `XxxDto` REST: son package-private y embeben hijos de forma ansiosa (`StandingsDto` trae todo), justo lo que GraphQL evita. |
| D5 | **Toda relación que se resuelve por id va en lote** con `@BatchMapping` (DataLoader de Spring for GraphQL), sin excepciones; la tabla "Estrategia por relación" de abajo lista cada campo del esquema. Detrás hay un método `findByIds` en cinco puertos (`Team`, `Attempt`, `Round`, `Challenge`, `Edition`), `findByRoundIds` en `AttemptRepository`, y un método `byIds` en el caso de uso de lectura de cada agregado. Las **listas por padre** (`Edition.challenges`, `Challenge.rounds`, `Attempt.appeals`, `Team.results`…) cuestan una lectura por padre, nunca por hijo; `Team.results` y `Slot.turn` cargan en un número fijo de lecturas por categoría. | Tratar solo `team` (el mínimo de la consigna, "al menos un punto") y documentar el resto como deuda: mismo patrón, poco más de trabajo, y en la presentación es mejor "lo medimos y lo arreglamos en todos lados" que "sabemos dónde falta". `team` se implementa primero por fila para **medirlo** y después se reemplaza; las dos mediciones quedan en `docs/graphql-vs-rest.md`. Lotes también para las listas por padre (`findByEditionIds`…): el número de padres en una consulta de la demo es 1 a 3; se deja como deuda con el camino escrito. |
| D6 | Errores: un `DataFetcherExceptionResolverAdapter` con esta tabla, y **solo** esta: `NotFoundException` → `NOT_FOUND` con el mensaje; `IllegalArgumentException` → `BAD_REQUEST` con el mensaje; base no disponible (`DataAccessResourceFailureException`, `CannotCreateTransactionException`) → `INTERNAL_ERROR` con el mismo texto `UNAVAILABLE` de REST y `warn` en el log; todo lo demás (`InconsistentStateException`, `CorruptDataException`, `IllegalStateException`, inesperados) → `INTERNAL_ERROR` con el texto genérico `INTERNAL_ERROR` de REST y `error` en el log. No es la tabla completa de `ApiExceptionHandler`: 409 y 422 son de escrituras y GraphQL no escribe (D2); `ErrorType` tampoco tiene `CONFLICT` ni `SERVICE_UNAVAILABLE`, por eso la base caída es `INTERNAL_ERROR` con mensaje propio. Las búsquedas singulares (`edition(id)`, `team(id)`…) son **nullable**: ante un id inexistente devuelven `null` en `data` y una entrada en `errors` con `extensions.classification = NOT_FOUND`. Un campo inexistente lo rechaza la validación de graphql-java antes de ejecutar (clasificación `ValidationError`). | Devolver HTTP 404/400: GraphQL responde 200 con `errors[]` por diseño (errores parciales por campo). Hacer los campos `!` no nulos: un 404 arrastraría a `null` toda la rama. |
| D7 | Fechas como `String` ISO-8601 (`LocalDate`/`LocalDateTime.toString()`), igual que el JSON REST. Sin `graphql-java-extended-scalars`. | Scalars `Date`/`DateTime` custom: una dependencia más y un scalar que REST no tiene; el valor es el mismo texto. |
| D8 | Profundidad máxima de consulta **10** con `MaxQueryDepthInstrumentation` (el esquema tiene ciclos `Challenge → Edition → challenges → …`), **sin contar la introspección**: la consulta `IntrospectionQuery` de GraphiQL anida `ofType` unas 7 veces bajo `__schema.types.fields.type` y supera 10; si la instrumentación la rechazara, GraphiQL queda sin docs ni autocompletado en la demo. Se verifica en la fase 0 al abrir GraphiQL; la instrumentación salta las operaciones cuyos campos raíz empiezan con `__` (spec 03 §2). GraphiQL habilitado en `application.yml` (siempre, es una consola de solo lectura sin más superficie que `/graphql`), no solo en el perfil demo. | Sin límite: una consulta recursiva hace trabajar al servidor sin tope (clase 13: "never wait forever"). Complejidad por costo de campo: más fino pero no hay métrica real para calibrarlo. |
| D9 | **No** se implementa la *subscription* opcional. Justificación en `DESIGN.md` cap. 7. | Requiere WebSocket y publicar eventos de dominio al cambiar un puntaje; `DESIGN.md` cap. 7 ya descartó un bus de eventos para el recálculo. Se deja el camino: `RecalculateStandingsUseCase` → evento → `Sinks.many()` → `@SubscriptionMapping`. |
| D10 | El front web **no cambia**. La demo usa GraphiQL y `curl`; la consigna dice que el esfuerzo se evalúa en el backend. **Plan B obligatorio**: la página `/graphiql` de Spring for GraphQL carga React y GraphiQL desde un CDN público (`unpkg.com`); sin internet en el aula queda en blanco. Los `curl` del README con las mismas consultas son el plan B y se ensayan (checklist). | Pantalla nueva consumiendo GraphQL: valor de demo, pero fuera de "lo mínimo indispensable". |
| D11 | No hay migración de base: `findByIds` es `findAllById` de Spring Data y `findByRoundIds` es `findByRoundIdIn`; ninguna entidad JPA tiene colecciones lazy (los hijos van en JSONB), así que no hay cargas escondidas que contar. | — |
| D12 | Los cuatro campos de captura de un turno (`rulebookVersion`, `attemptRule`, `acceptsResult`, `attempts`) se agrupan en un tipo `Turn` bajo `Slot.turn`, en vez de ir sueltos en `Slot`. Agregada después de la primera revisión, por eso el número. | Sueltos: cuatro resolvers sobre el mismo caso de uso, o cuatro lotes que repiten la misma carga. Un solo campo = un solo lote por categoría. Es el mismo agrupamiento que `GET /slots/{id}/results`. |

### Estrategia por relación (lo que el criterio 3 y la presentación afirman)

Cada campo no escalar del esquema cae en una de tres categorías. "Lote" es un
`@BatchMapping` con DataLoader; "por padre" es una lectura por objeto padre
(no por hijo); "fijo" es un número constante de lecturas por categoría. El
detalle (caso de uso y clave) está en el mapa de resolución de la spec 01.

| Relación | Estrategia | Caso de uso |
| --- | --- | --- |
| `Registration.team`, `Slot.team`, `Attempt.team`, `Appeal.team`, `StandingsEntry.team` | lote por `teamId` | `QueryTeamsUseCase.byIds` |
| `RoundResult.attempt`, `Appeal.attempt` | lote por `attemptId` (trae el breakdown) | `GetAttemptBreakdownUseCase.byIds` |
| `Attempt.breakdown` | lote por `id` cuando el view no lo trae; 0 lecturas si lo trae | `GetAttemptBreakdownUseCase.byIds` |
| `RoundResult.round`, `Attempt.round` | lote por `roundId` | `QueryRoundsUseCase.byIds` |
| `Round.challenge` | lote por `challengeId` | `GetChallengeUseCase.byIds` |
| `Challenge.edition`, `Round.category` | lote por `editionId` (`category` filtra la edición cargada) | `QueryEditionsUseCase.byIds` |
| `Slot.turn` | lote por `roundId`, fijo por (desafío, categoría) | `QueryTurnAttemptsUseCase.ofRounds` |
| `Team.results` | fijo (4–5 lecturas) | `QueryTeamResultsUseCase` |
| `Registration.category`, `Challenge.currentRulebook`/`rulebooks`/`rulebook` | 0 lecturas: sale del padre | — |
| `Edition.challenges`, `Edition.registrations`, `Challenge.rounds`, `Challenge.appeals`, `Challenge.standings`, `Attempt.appeals` | por padre (una lectura por padre) | casos de uso existentes |

Lo que un test de conteo afirma, por lo tanto, es: para una lista de N filas
(entradas de la tabla, slots de una ronda, resultados de un equipo), pedir
una relación "lote" o "fijo" cuesta lo mismo con N = 1 que con N = 3.

## Orden de implementación

Trabajar en una rama `entrega-3/graphql` con commits por fase (prefijos
`feat:`, `test:`, `docs:` como en el historial) y PR a `main`; la CI
(`mvn -B verify` + tests JS) tiene que estar verde antes de mergear.

| Fase | Spec | Resultado | Verificación mínima (desde la raíz) |
| --- | --- | --- | --- |
| 0 | 03 §1 | Dependencias, `schema.graphqls` con `editions`, GraphiQL **abierto en el navegador con docs y autocompletado** (D8) y **sin red** (D10), `ApiTest` con `HttpGraphQlTester`; un test que consulta `editions` | `mvn -B -pl roboleague-api -am test` (lento: `-am` arrastra los tests de infrastructure con Testcontainers; para iterar, `-Dtest=GraphQlSmokeTest -Dsurefire.failIfNoSpecifiedTests=false`) |
| 1 | 02 | `findByIds` en los cinco puertos y `findByRoundIds` en `AttemptRepository` (puerto + JPA + memoria), `CategoryRulebook` sobre `findByRoundIds`, `QueryTeamsUseCase`, `QueryTeamResultsUseCase`, `byIds` en rounds/breakdown/challenge/editions, `QueryTurnAttemptsUseCase.ofRounds`, con tests | `mvn -B -pl roboleague-application -am test` y `mvn -B -pl roboleague-infrastructure -am test` |
| 2 | 01 + 03 §2–4 | Esquema completo, views, resolvers, errores, límite de profundidad; el informe de inspección del esquema al arrancar no lista campos sin resolver | `mvn -B -pl roboleague-api -am test` |
| 3 | 03 §5 | Todos los lotes de la tabla "Estrategia por relación"; test de evidencia N+1 con estadísticas de Hibernate por lote; medición antes/después de `team` anotada | ídem |
| 4 | 03 §6 | Suite GraphQL completa (consultas principales, consulta anidada, error por entidad, error por campo, profundidad) | `mvn -B verify` |
| 5 | 04 | README, `DESIGN.md` (2.33, cap. 3, 5, 7 y nuevo cap. 8), `docs/graphql-vs-rest.md` con mediciones sobre la demo, AGENTS, skill de revisión, estado de estas specs | revisar enlaces y comandos; `mvn -B verify` de cierre |
| 6 | 05 | Guion de presentación con los números medidos | — |

Al cerrar cada fase, correr el procedimiento `.agents/skills/verify-change/SKILL.md`
e informar qué se ejecutó y qué quedó pendiente. Antes del PR, aplicar
`.agents/skills/review-change/SKILL.md` sobre el diff completo.

## Reglas de trabajo del equipo (hoja de ruta del 29/09)

El equipo las fijó para la Entrega 2 y siguen vigentes; no están en
`AGENTS.md` porque son de proceso, no de código:

- Nada entra a `main` sin la CI en verde y una **review de alguien que no
  escribió el cambio** (en la Entrega 2, de otro frente). El PR de GraphQL la
  necesita aunque lo implemente un agente.
- Si cambia lógica de negocio, **primero el test y después el fix**.
- Un cambio a una interfaz compartida (acá: el puerto `TeamRepository` y la
  firma de `QueryRoundsUseCase`) se **avisa en el grupo antes del PR**.
- Cada decisión de diseño va a `DESIGN.md` **el mismo día**, no al final:
  cada fase que cierra escribe su parte de 2.33 / cap. 8, no se deja todo para
  la fase 5.
- Cada integrante revisa un PR ajeno y escribe su sección del `DESIGN.md`
  (patrón, dónde y por qué; alternativas; patrones no aplicados; clases
  agregadas/modificadas, refactors y deuda).

Reglas de la cursada (`reglas-del-proyecto.md` de las notas de clase, citadas
por número en `DESIGN.md`) que esta entrega toca:

| Regla | Qué dice | Cómo se cumple acá |
| --- | --- | --- |
| #2 | Un constructor tiene como mucho 3 parámetros de datos | Aplica al modelo. Los `*View` son vistas planas como los DTO REST (DESIGN 2.15: "la regla de tres parámetros se aplica al modelo") y los casos de uso reciben puertos, no datos (hoy hay casos de uso con hasta 7). `TeamTurn(round, slot, attempts)` cumple. |
| #11 | Ningún tipo de un tercero cruza al dominio | `spring-graphql` solo en api; domain y application no cambian de dependencias (D1). |
| #18 | Sin interfaces "por las dudas" | No se agrega ninguna interfaz: los resolvers reciben casos de uso concretos, los views son records. |
| #24 | Ningún método devuelve un valor de relleno | Un id inexistente devuelve `null` **más** una entrada en `errors` (D6); nunca un objeto vacío. |
| #28 | Falla esperable como resultado, no excepción | Las lecturas ya usan `NotFoundException` (REST → 404); GraphQL la traduce igual (→ `NOT_FOUND`). No se introduce un resultado `sealed` nuevo para lecturas porque no existe en REST (regla #27, no duplicar). |

## Reparto sugerido entre los cinco

El que presenta se elige al azar y presenta **todo**: cada integrante ensaya
el guion completo de la spec 05 (checklist). El reparto de abajo es para el
trabajo y para la profundidad: la parte que cada uno hizo o revisó es donde
puede entrar al código en vivo si la cátedra lo pide. Una división que
respeta las fases:

| Integrante | Fase | Defiende en la presentación |
| --- | --- | --- |
| Integrante 1 | Esquema (spec 01, fase 2 parte SDL) | Qué se expone, qué no y por qué; nullabilidad; enums |
| Integrante 2 | Application y puertos (spec 02, fase 1) | Los `findByIds`, los `byIds`/`ofRounds`, `CategoryRulebook` en una lectura, `QueryTeamResults` en lecturas fijas, tests de cobertura |
| Integrante 3 | Resolvers y lotes (spec 03 §2 y §5, fases 2–3) | Los `@BatchMapping`, la medición antes/después de `team`, por qué `Turn` (D12) |
| Integrante 4 | Errores y tests (spec 03 §3 y §6, fase 4) | Tabla de errores, test de campo/entidad inexistente, profundidad |
| Integrante 5 | Docs y comparación (spec 04, fase 5) | `graphql-vs-rest.md`, DESIGN 2.33 y cap. 8, README |

Si lo implementa un agente, el reparto es de **revisión**: cada uno revisa y
ensaya la parte que le toca.

## Reglas de trabajo para quien implementa

- Leer `AGENTS.md` de la raíz y de `roboleague-api`, `roboleague-application`
  y `roboleague-domain` antes de tocar cada módulo. No introducir Spring,
  GraphQL ni Jackson en domain/application.
- Los casos de uso de lectura nuevos siguen el patrón de `QueryRoundsUseCase`:
  validan padres (`NotFoundException(recurso, id)`), no escriben, devuelven
  tipos del dominio o records de application sin conceptos HTTP/GraphQL.
- `roboleague-application` exige cobertura mínima 90 % líneas / 89 % ramas de
  sus propios tests (`pom.xml`): cada caso de uso nuevo lleva su test con los
  repositorios en memoria, incluidos los rechazos.
- Un `*View` nuevo se construye con `static from(...)`, como los DTO REST.
  Ningún resolver accede a repositorios; solo a casos de uso.
- Los tests de API comparten contexto y base (`ApiTest`): usar ids con prefijo
  propio (`*-gql-*`), nunca depender de la demo ni de una base vacía.
- Proyecto en Spring Boot **4.1.1** y Jackson `tools.jackson`: los paquetes de
  auto-configuración de test cambiaron respecto de Boot 3. Verificar cada
  import en el jar de `~/.m2` antes de usarlo (spec 03 §1).
- Documentar en español; nombres de tipos y métodos en inglés.
- No editar migraciones Flyway existentes ni crear una nueva (D11).

## Criterios de aceptación globales

1. `POST /graphql` responde la consulta de la consigna en un solo pedido:
   equipo → resultados por ronda → intento → desglose explicado (ítems, fuentes,
   tope, revisiones), sobre los datos de la demo.
2. Cada campo del esquema se resuelve llamando a un caso de uso. Control
   manual en la fase 5 (no un test): `grep` de `Repository` dentro de
   `com.roboleague.api.graphql` no devuelve nada.
3. Un test por fila "lote" o "fijo" de la tabla "Estrategia por relación"
   demuestra que el número de sentencias SQL no crece con la cantidad de
   filas del nivel (entradas de la tabla, slots de **una** ronda, resultados
   de un equipo): `team`, `attempt` (que cubre `breakdown`), `round`,
   `challenge`, `edition`/`category` y `turn`.
4. Un test pide un campo inexistente y otro una entidad inexistente; los dos
   verifican la clasificación del error y que `data` no trae valores inventados.
5. `mvn -B verify` verde en CI; los tests GraphQL corren en el mismo job.
6. README documenta endpoint, GraphiQL, tres consultas de ejemplo y la forma
   de los errores; `docs/graphql-vs-rest.md` tiene los dos casos con números
   medidos; `DESIGN.md` registra D1–D12 con alternativas.
7. La demo (`--spring.profiles.active=demo`) sigue cargando igual
   (`DemoFixtureTest`) y GraphiQL abre en `http://localhost:8080/graphiql`.

## Checklist de entrega (para el equipo)

- [x] PR mergeado y CI verde; anotar el **hash del último commit** en la entrega.
- [x] `git tag entrega-3` sobre ese commit y `git tag entrega-2 7f321c5` (merge
      del PR #20, 13/10/2026, el commit que se entregó).
- [ ] PR revisado por alguien que no escribió el cambio (regla del equipo).
- [ ] Cada fase dejó su parte de `DESIGN.md` escrita el día que cerró; cada
      integrante escribió o revisó la sección que le toca del reparto.
- [ ] README y `docs/graphql-vs-rest.md` revisados por alguien que no los escribió.
- [ ] Todos los integrantes ensayaron el guion de la spec 05 (presenta uno al azar).
- [ ] Demo probada en la máquina que se lleva a clase: Docker, puerto 5432 libre,
      `java -jar ... --spring.profiles.active=demo`, GraphiQL abre, las consultas
      del guion responden.
- [ ] Demo probada **sin red** (wifi apagado): si GraphiQL queda en blanco (CDN),
      los `curl` del README responden las mismas consultas; el que presenta los
      tiene en el historial de la terminal.

## Implementación realizada

Las fases 0 a 6 están en `main`. Clases agregadas y modificadas,
refactorizaciones y deuda: `DESIGN.md` cap. 8. Desviaciones respecto de este plan:

- **D4: los resolvers dependen de puertos de entrada, no de casos de uso
  concretos.** La devolución de la Entrega 2 pidió puertos de entrada; desde el
  PR #28 cada caso de uso implementa una interfaz de `port.in` y los resolvers
  solo la conocen (DESIGN 2.34, `InputPortBoundaryTest`).
- **Tests de lote:** el `GraphQlBatchingTest` del plan quedó en dos clases por
  área, `GraphQlTeamBatchingTest` y `GraphQlEvaluationBatchingTest`.
- **Presentación (spec 05):** la consigna publicada en el campus pide un video
  de 8 a 12 minutos en el que hablan los cinco, en lugar de la presentación oral
  de un integrante al azar. El guion se reescribió para el video.
- **Límite conocido:** el lote de `team` agrega una sola lectura, pero
  `QueryRegistrationsUseCase.list` y `CategoryResultsReader` siguen leyendo un
  equipo por inscripción. El costo total de `Edition.registrations` y del
  cálculo de la tabla crece con los inscriptos; el de la relación `team`, no.

## Fuera de alcance

Mutaciones por GraphQL, subscription, autenticación, Relay/connections,
persisted queries, caché por campo, federación, cambios al front web, cambios
de esquema de base. Si algo de esto aparece como necesario, registrarlo como
deuda en `DESIGN.md` cap. 8 en lugar de implementarlo.
