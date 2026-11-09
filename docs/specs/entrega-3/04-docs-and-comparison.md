# 04 — Documentación: README, DESIGN, comparación REST/GraphQL, AGENTS

Estado: **implementada** (README, DESIGN y comparación en el PR #31; AGENTS y estado de las specs en el PR que cierra la entrega). Fase 5 del README. Se hace **después** de que la suite
pase, porque los números de la comparación se miden sobre la demo real.

## 1. `README.md`

### Tabla "Contratos"

Dos filas al final:

| Método | Ruta | Éxito | Errores propios |
| --- | --- | --- | --- |
| POST | `/graphql` | 200 con `{data, errors}`; ver "API GraphQL" | Siempre 200: un id inexistente es `errors[].extensions.classification = NOT_FOUND` con `data.<campo> = null`; un campo inexistente o argumento inválido, `ValidationError` sin `data`; un dato mal formado, `BAD_REQUEST` |
| GET | `/graphiql` | 200, consola interactiva | — |

### Sección nueva "API GraphQL" (después de "Tabla de posiciones")

Contenido obligatorio, en este orden:

1. Qué es: segundo adaptador de entrada, **solo consultas**, sobre los mismos
   casos de uso que REST (D1, D2). Endpoint `/graphql`, consola `/graphiql`.
   Esquema en `roboleague-api/src/main/resources/graphql/schema.graphqls`.
2. Tres consultas de ejemplo con `curl` sobre la demo, con su respuesta
   recortada (ejecutarlas y pegar la salida real):
   - **A, la de la consigna**: `team(id: "t-b") { name results(challengeId: "ch-maze") { round { number name } attempts { id status score breakdown { items { concept rawMetric formula subtotal } bySource { label subtotal } revisions { number reason total } } } } }`;
   - **B, podio**: `standings(challengeId: "ch-maze", categoryId: "cat-junior") { official { number entries { position teamName total explanation } } }`;
   - **C, navegación inversa**: `appeal(id: "demo-43") { status reason attempt { score breakdown { revisions { number total reason } } team { name institution } } }`.
3. Forma de los errores con un ejemplo real de cada tipo (`NOT_FOUND`,
   `ValidationError`, `BAD_REQUEST`, `INTERNAL_ERROR` genérico).
4. Límite de profundidad 10 y por qué (D8).
5. Qué no expone y dónde está en REST (spec 01 "Qué se expone y qué no").
6. Enlace a `docs/graphql-vs-rest.md`.

### "Tests"

En la tabla de tipos de test, la fila API pasa a decir "Rutas, códigos HTTP,
JSON, consultas GraphQL y el cableado entero (`ApiTest`: MockMvc +
`HttpGraphQlTester`)". Mencionar `GraphQlBatchingTest` como la evidencia del N+1.

### "Cómo sumar lo tuyo"

Párrafo nuevo **"Un campo GraphQL."**: agregar el campo al `schema.graphqls`;
si es escalar, al record `*View`; si es relación, un `@SchemaMapping` en el
`XxxQueries` del tipo padre que llame a un caso de uso (nunca a un
repositorio); si se resuelve por fila de una lista, `@BatchMapping` con un
caso de uso `byIds`; un test en `api/graphql`; una fila en la comparación si
cambia un caso. Al arrancar, el informe de inspección del esquema tiene que
seguir sin campos sin resolver.

### "Módulos"

`roboleague-api`: "Composition root de Spring Boot, controllers REST, **API
GraphQL** (`api.graphql`), DTOs, manejo de errores, `DemoFixture`."

## 2. `docs/graphql-vs-rest.md` (entregable "Comparación con REST")

Documento breve (dos o tres pantallas). Estructura fija:

```
# REST y GraphQL en RoboLeague: dos casos medidos

Cómo se midió: demo cargada (`--spring.profiles.active=demo`), `curl -s -w '%{size_download}'`
para bytes de REST, `curl` con el documento GraphQL para GraphQL. Fecha y commit.

## Caso A: pantalla de resultados en vivo de un equipo
Cliente: la pantalla de pista muestra, para TitanTeam (t-b) en Laberinto, cada ronda con el
puntaje del intento y el desglose explicado.
Qué necesita: nombre del equipo; por ronda: número, nombre; por intento: id, estado, puntaje,
ítems (concepto, métrica, fórmula, subtotal), aporte por fuente, revisiones.

| | REST | GraphQL |
| Peticiones | 1 (`GET /challenges/ch-maze/rounds?categoryId=cat-junior`) + 4 (`GET /slots/{slot}/results`, una por turno de t-b) + 4 (`GET /attempts/{id}/breakdown`) = 9 | 1 |
| Bytes recibidos | suma medida | medido |
| Datos de más (sobre-obtención) | las rondas traen los slots de los otros equipos, pistas, jueces, horarios; `results` trae `acceptsResult`, `attemptRule`; `breakdown` trae `awaiting`, `notes` | ninguno: solo los campos pedidos |
| Datos de menos (sub-obtención) | la ronda no trae los intentos ni el desglose: cada uno es otro viaje (y el cliente tiene que saber qué slot es de t-b) | — |
| Round-trips dependientes | 3 niveles (rondas → slots → intentos) | 0 |

## Caso B: widget de podio para prensa
Cliente: una página externa muestra el podio oficial de Laberinto: posición, nombre, total,
explicación.
| Peticiones | 1 (`GET /challenges/ch-maze/standings?categoryId=cat-junior`) | 1 |
| Bytes | medido (trae `versions`, `latest` y `official` completos con `considered`/`discarded` de cada fila, `pending`) | medido (4 campos × 3 filas) |
| Sobre-obtención | todo lo anterior; `official` y `latest` son la misma versión repetida | ninguna |
| Sub-obtención | la fila trae `teamId`/`teamName` pero no institución ni robot; para eso hace falta `GET /editions/ed-1/registrations/{teamId}` por fila (3 más) | `team { institution }` en la misma consulta, resuelto por lotes |

## Qué gana REST
Cacheable por URL y por proxies HTTP; códigos de estado por recurso; cuerpos fijos fáciles de
documentar y validar; las escrituras siguen ahí (resultados sealed → 201/422/409).

## Evidencia del N+1
Consulta: `standings { latest { entries { team { institution } } } }` con 3 equipos.
| Implementación | Sentencias SQL para `team` | Dónde se mide |
| `@SchemaMapping` por fila (`findById`) | 3 (N) | medido en la fase 3 antes del cambio |
| `@BatchMapping` + `TeamRepository.findByIds` | 1 | `GraphQlBatchingTest` (asserta la diferencia == 1) |
Los demás lotes, medidos en lote directamente (misma tabla, una fila por campo): `RoundResult.attempt`
(2), `RoundResult.round` (1), `Round.challenge` (1), `Round.category` (1), `Attempt.breakdown` (0 extra
si el padre lo trajo), `Slot.turn` (4 por categoría). Anotar el número real de cada test.
Por qué existe: cada caso de uso es una transacción propia (`TransactionalUseCases`), así que no hay
sesión Hibernate compartida entre filas; GraphQL resuelve cada relación fila por fila salvo que se
declare el lote. `Team.results` no lo sufre porque `QueryTeamResultsUseCase` carga rondas e intentos
del equipo en 4–5 lecturas fijas. Las listas por padre (`Edition.challenges`, `Attempt.appeals`…)
cuestan una lectura por padre (README, "Estrategia por relación"); su lote es deuda (DESIGN cap. 8).

## Decisiones del esquema (resumen; detalle en DESIGN 2.33)
Qué se expone, qué no y por qué (reglamento completo, mutaciones, métricas de apelación).
```

Las celdas "medido" se completan con números reales; no dejar placeholders.

## 3. `DESIGN.md`

### Nueva sección `2.33 API GraphQL de consulta como segundo adaptador (Entrega 3)`

Con el formato de 2.x (Problema / Solución implementada / Pros / Contras /
Alternativas). Debe cubrir D1–D8 y D12 del README con su alternativa y por qué, y
además:

- cómo se mantiene la regla de dependencias: el esquema y los `*View` viven en
  api; application y domain no cambian de dependencias (solo ganan un método
  de puerto y dos casos de uso de lectura, Java plano);
- por qué los resolvers llaman a casos de uso y no a repositorios (misma regla
  que los controllers; el criterio es verificable con `grep`);
- la duplicación consciente de la tabla de errores entre
  `ApiExceptionHandler` y `GraphQlErrorResolver` (dos contratos de salida);
- el N+1: dónde aparece, por qué (transacción por caso de uso), cómo se trató
  (`@BatchMapping` = DataLoader) y dónde se dejó;
- las reglas de la cursada citadas por número, como en el resto de 2.x:
  #11 (GraphQL solo en api), #18 (sin interfaces nuevas), #2 (los views son
  vistas planas, exentos como los DTO), #24 (`null` + `errors`, nunca un
  objeto de relleno), #28 (misma `NotFoundException` que REST; no se duplica
  un resultado `sealed` para lecturas, #27). La tabla del README de estas
  specs tiene el texto corto de cada una.

Escribir esta sección **por fases, el día que cada una cierra** (regla del
equipo "cada decisión al DESIGN el mismo día"): la fase 1 deja D5/D11, la 2
deja D1–D4 y D6–D8, la 3 completa el N+1 con los números, la 4 la tabla de
errores. La fase 5 solo revisa y agrega el cap. 8.

### Capítulo 3 (matriz)

Fila nueva: "**API GraphQL de consulta junto a REST (D1–D5)** | un cliente pide
exactamente lo que necesita en un viaje; el dominio no cambia; los resolvers
reutilizan los casos de uso | dos adaptadores que mantener; N+1 latente en
cada relación por fila; errores en 200 con `errors[]` | GraphQL en lugar de
REST, o mutaciones por GraphQL: rechazadas (D2)".

### Capítulo 5 (verificación)

Ítem nuevo "**Pruebas de API GraphQL**" listando los tests de la spec 03 §6 y
qué prueba cada uno; `GraphQlBatchingTest` como evidencia del N+1.

### Capítulo 7 (no aplicado)

Filas nuevas, con "Por qué no" y "Consecuencia asumida":
- Subscription GraphQL para el ranking en vivo (D9).
- Mutaciones por GraphQL (D2).
- Code-first / esquema generado desde clases (D3).
- Scalars `Date`/`DateTime` (D7).
- DataLoader en las listas por padre (`Edition.challenges`, `Challenge.rounds`,
  `Attempt.appeals`…, que necesitarían `findByEditionIds`, `findByChallengeIds`,
  `findByAttemptIds`): una consulta de la demo tiene 1 a 3 padres de cada tipo;
  consecuencia: una lectura por padre, con el camino ya probado en los lotes por id.
- Subclase o configuración de `MaxQueryDepthInstrumentation` por complejidad
  (`MaxQueryComplexityInstrumentation`): sin métrica para calibrar costos por campo.
- Módulo Maven aparte para GraphQL (D1).
- Relay connections / paginación: las listas son chicas (equipos de una
  categoría, rondas de un desafío); consecuencia: una edición grande devuelve
  todo.
- Persisted queries / caché por campo: sin métrica que lo justifique.

### Capítulo nuevo `8. Propuesta de valor (Entrega 3): clases, refactorizaciones y deuda`

Mismo formato que el capítulo 6 (**Clases agregadas / Clases modificadas /
Refactorizaciones realizadas / Deuda técnica que se decidió no resolver**),
con los nombres reales de lo que se implementó. Deuda mínima a registrar:
listas por padre sin lote; sin límite de tamaño de respuesta; sin subscription;
sin autenticación (igual que REST); GraphiQL depende de un CDN (plan B `curl`).

Actualizar el índice del capítulo 1 ("Cómo leerlo") para mencionar el
capítulo 8.

## 4. `AGENTS.md`

### Raíz

- Tabla de módulos, fila `roboleague-api`: "REST, **GraphQL de consulta**,
  DTOs, composition root, transacciones y demo".
- En "Mapa y límites": una línea "GraphQL (`api.graphql`) es otro adaptador
  de entrada: solo lecturas, resolvers que llaman casos de uso, views propios;
  `team` se resuelve por lotes. Ver `roboleague-api/AGENTS.md`."

### `roboleague-api/AGENTS.md`

Sección nueva **"API GraphQL"** con las reglas operativas:
- esquema en `schema.graphqls`; schema-first; solo `type Query`;
- `XxxQueries` con `@QueryMapping`/`@SchemaMapping`/`@BatchMapping`; llamar
  casos de uso, nunca repositorios; convertir ids en el resolver;
- views en `api.graphql.view`, records planos con `from(...)`, sin relaciones
  embebidas; nombres de componentes = campos del SDL;
- errores por `GraphQlErrorResolver` con la misma tabla que `ApiExceptionHandler`;
  búsquedas singulares nullable; no exponer mensajes internos;
- relaciones por fila de lista → `@BatchMapping` + caso de uso `byIds`;
- `ApiTest` da `HttpGraphQlTester` y estadísticas de Hibernate; tests en
  `api/graphql` con ids `-gql-`;
- al arrancar, el informe de inspección del esquema no debe listar campos sin
  resolver; GraphiQL solo es una consola, no un contrato.

### `roboleague-application/AGENTS.md`

En "Inscripción y actualización" o un bloque de lecturas: `QueryTeamsUseCase`
(`get`, `byIds` con `InconsistentStateException` para referencias rotas),
`QueryTeamResultsUseCase` (lecturas fijas, orden de rondas e intentos) y
`QueryRoundsUseCase.bySlot`.

### `.agents/skills/review-change/SKILL.md`

Un bullet en "Seguir el comportamiento": "GraphQL: cada campo del esquema
tiene resolver o componente de view; resolvers sin repositorios; relaciones
por fila con `@BatchMapping`; errores clasificados sin mensajes internos;
tests con `HttpGraphQlTester` e ids propios."

## 5. Estas specs

Al terminar, cambiar "Estado: planificada" por "Estado: implementada" en cada
archivo y agregar al README de este directorio una sección "Implementación
realizada" con las desviaciones respecto del plan (como hace
`docs/specs/frente-3/05-...`). Si una decisión D1–D12 cambió durante la
implementación, decir cuál y por qué, acá y en `DESIGN.md`.
