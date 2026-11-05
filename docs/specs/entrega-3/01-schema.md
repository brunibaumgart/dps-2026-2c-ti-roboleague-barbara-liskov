# 01 — Esquema GraphQL

Estado: planificada. Fase 2 del README. Depende de la spec 02 (casos de uso
nuevos) para `Team.results`, `team(id)`, `slot(id)` y el lote de `team`.

## Qué se expone y qué no

Se expone **lo que ya exponen los contratos REST de lectura** (README, tabla
"Contratos"), reorganizado como grafo: desde cualquier nodo se navega a sus
vecinos y cada relación se resuelve solo si la consulta la pide. No se expone:

- escrituras (D2);
- el cuerpo completo de un reglamento (`RulebookDto` con cada regla y métrica):
  GraphQL muestra versión, fuentes exigidas, regla de intentos, selección de
  rondas y criterios de desempate, que es lo que explica una tabla; el detalle
  de reglas sigue en `GET /challenges/{id}/rulebook/versions/{v}` y se cita en
  `docs/graphql-vs-rest.md` como decisión ("qué no se expone y por qué");
- métricas crudas de una apelación (`revisedMetrics`): quedan en REST;
- ids internos de JPA, versiones de bloqueo optimista, eventos de auditoría.

Ids: tipo `ID`, mismo texto que REST. Fechas: `String` ISO-8601 (D7).
Puntos: `Float` (`Points.doubleValue()`, como los DTO). Enums: mismas
constantes que el enum Java (`name()`), copiadas abajo desde el código.

## Archivo

`roboleague-api/src/main/resources/graphql/schema.graphqls` (ubicación por
defecto de Spring for GraphQL). Un solo archivo; comentarios `"""` en cada
tipo con una línea en español que diga qué es y de qué caso de uso sale.

## SDL completo

```graphql
type Query {
  "Todas las ediciones, por id."
  editions: [Edition!]!
  edition(id: ID!): Edition
  challenge(id: ID!): Challenge
  team(id: ID!): Team
  round(id: ID!): Round
  "Un turno, buscado por su id de slot."
  slot(id: ID!): Slot
  "Un intento por su id <slot>-<numero>."
  attempt(id: ID!): Attempt
  appeal(id: ID!): Appeal
  "La tabla de un desafío en una categoría; existe aunque nunca se haya calculado."
  standings(challengeId: ID!, categoryId: ID!): Standings!
}

type Edition {
  id: ID!
  name: String!
  editionNumber: Int!
  tournament: Tournament!
  startDate: String!
  endDate: String!
  categories: [Category!]!
  challenges: [Challenge!]!
  "Inscripciones de la edición; con categoryId, solo las de esa categoría (400 si no la ofrece)."
  registrations(categoryId: ID): [Registration!]!
}

type Tournament { id: ID!, name: String!, description: String!, season: Season! }
type Season { id: ID!, year: Int!, name: String! }

type Category {
  id: ID!
  name: String!
  minMembers: Int!
  maxMembers: Int!
  minAge: Int!
  maxAge: Int
  maxWeightGrams: Float!
  maxLengthMm: Float!
  maxWidthMm: Float!
  maxHeightMm: Float!
}

type Registration {
  editionId: ID!
  categoryId: ID!
  category: Category!
  registeredAt: String!
  referenceDate: String!
  team: Team!
}

type Team {
  id: ID!
  name: String!
  institution: String!
  members: [Member!]!
  robot: Robot!
  documentation: Documentation!
  "Lo que el equipo corrió en un desafío (y opcionalmente una categoría), ronda por ronda: la consulta de la consigna."
  results(challengeId: ID!, categoryId: ID): [TeamTurn!]!
}

type Member { id: ID!, fullName: String!, birthDate: String!, role: String! }
type Robot {
  id: ID!
  name: String!
  weightGrams: Float!
  lengthMm: Float!
  widthMm: Float!
  heightMm: Float!
  actuatorCount: Int!
  sensors: [String!]!
}
type Documentation {
  documents: [Document!]!
  verified: Boolean!
  verifiedAt: String
  verifiedBy: String
  revocationReason: String
}
type Document { kind: String!, reference: String! }

"Un turno de un equipo en una ronda, con sus intentos en el orden en que se abrieron."
type TeamTurn {
  round: Round!
  slot: Slot!
  attempts: [Attempt!]!
}

type Challenge {
  id: ID!
  name: String!
  editionId: ID!
  edition: Edition!
  currentRulebook: Rulebook!
  rulebooks: [Rulebook!]!
  rulebook(version: Int!): Rulebook
  rounds(categoryId: ID): [Round!]!
  standings(categoryId: ID!): Standings!
  appeals(categoryId: ID!): [Appeal!]!
}

type Rulebook {
  version: Int!
  requiredSources: [ResultSource!]!
  "Cuántos intentos admite por turno y cuál cuenta, como lo dice el dominio (AttemptSelection.describe)."
  attemptRule: String!
  "Qué rondas cuentan (RoundSelection.describe)."
  selectionRule: String!
  "Códigos de los criterios de desempate, en orden (RulebookDefinition.ranking.criteria)."
  tieBreakCriteria: [String!]!
}

type Round {
  id: ID!
  number: Int!
  name: String!
  status: RoundStatus!
  challengeId: ID!
  editionId: ID!
  categoryId: ID!
  challenge: Challenge!
  category: Category!
  slots: [Slot!]!
}

type Slot {
  id: ID!
  roundId: ID!
  teamId: ID!
  team: Team!
  track: Track!
  judges: [Judge!]!
  startTime: String!
  endTime: String!
  intervalSeconds: Int!
  status: SlotStatus!
  cancellation: Cancellation
  "Lo que ve quien carga resultados: reglamento, regla de intentos y los intentos del turno."
  turn: Turn!
}

"Un turno visto desde la captura (mismo contenido que GET /slots/{id}/results)."
type Turn {
  "Versión del reglamento con la que juega la categoría del turno."
  rulebookVersion: Int!
  attemptRule: String!
  acceptsResult: Boolean!
  attempts: [Attempt!]!
}

type Track { id: ID!, name: String!, surfaceType: String!, active: Boolean! }
type Judge { id: ID!, fullName: String!, specialty: String! }
type Cancellation { actorId: String!, reason: String!, cancelledAt: String! }

type Attempt {
  id: ID!
  number: Int!
  slotId: ID!
  roundId: ID!
  teamId: ID!
  challengeId: ID!
  rulebookVersion: Int!
  status: AttemptStatus!
  received: [ResultSource!]!
  "Puntaje que cuenta para la tabla; null mientras espera una fuente o si está descalificado."
  score: Float
  team: Team!
  round: Round!
  breakdown: ScoreBreakdown!
  appeals: [Appeal!]!
}

"Cómo se explica el puntaje: lo que falta, cada ítem (tope y piso incluidos), lo que aportó cada fuente y cada revisión."
type ScoreBreakdown {
  total: Float
  awaiting: [ResultSource!]!
  items: [ScoreItem!]!
  notes: [String!]!
  bySource: [SourceContribution!]!
  revisions: [Revision!]!
}
type ScoreItem { concept: String!, rawMetric: String!, formula: String!, subtotal: Float! }
type SourceContribution { source: ResultSource!, label: String!, subtotal: Float!, items: [ScoreItem!]! }
type Revision {
  number: Int!
  rulebookVersion: Int!
  authorId: String!
  reason: String!
  total: Float!
  timestamp: String!
}

type Standings {
  challengeId: ID!
  categoryId: ID!
  "Todas las versiones sin filas, de la más vieja a la más nueva."
  versions: [StandingsVersionSummary!]!
  latest: StandingsVersion
  official: StandingsVersion
  pending: Pending!
}
type StandingsVersionSummary {
  number: Int!
  calculatedAt: String!
  status: StandingsStatus!
  publication: Publication
}
type StandingsVersion {
  number: Int!
  calculatedAt: String!
  status: StandingsStatus!
  publication: Publication
  rulebookVersion: Int!
  entries: [StandingsEntry!]!
}
type Publication { publishedAt: String!, publishedBy: String!, notes: String! }
type Pending { openAppeals: Int!, unfinishedTurns: Int!, outdated: Boolean! }

type StandingsEntry {
  position: Int!
  explanation: String!
  teamId: ID!
  "Nombre tal como lo guardó la versión (TeamIdentity): no cuesta una consulta."
  teamName: String!
  "El equipo canónico de hoy: se resuelve por lotes (D5)."
  team: Team!
  total: Float!
  selectionRule: String!
  attemptRule: String!
  considered: [RoundResult!]!
  discarded: [RoundResult!]!
}
type RoundResult {
  roundId: ID!
  attemptId: ID!
  total: Float!
  otherAttemptIds: [ID!]!
  round: Round!
  attempt: Attempt!
}

type Appeal {
  id: ID!
  attemptId: ID!
  teamId: ID!
  reason: String!
  evidence: String!
  status: AppealStatus!
  reviewerId: String
  submittedAt: String!
  resolutionNotes: String
  resolvedAt: String
  attempt: Attempt!
  team: Team!
}

enum ResultSource { AUTOMATIC_MEASUREMENTS, JUDGE_PANEL }
enum AttemptStatus { SCHEDULED, AWAITING_SOURCES, EVALUATED, UNDER_APPEAL, ADJUSTED, DISQUALIFIED }
enum RoundStatus { SCHEDULED, IN_PROGRESS, COMPLETED }
enum SlotStatus { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }
enum StandingsStatus { PROVISIONAL, OFFICIAL, REPLACED }
enum AppealStatus { PENDING, UNDER_REVIEW, ACCEPTED, REJECTED }
```

Las constantes de los enums se verificaron contra el código el 5/11/2026
(`Slot.SlotStatus`, `Round.RoundStatus`, `Attempt.AttemptStatus`,
`StandingsVersion.Status`, `ResultSource` y los `getStatusName()` de los
estados de `Appeal`). `Documentation.getDocuments()` devuelve
`Map<String,String>`: se traduce a `[Document{kind, reference}]`. Si al
implementar una constante no coincide, corregir el SDL, no el dominio.

## Mapa de resolución (qué caso de uso responde cada campo)

Los campos escalares de cada tipo salen del record `*View` que devuelve el
resolver del padre. Solo las relaciones de esta tabla necesitan un método
`@SchemaMapping`/`@BatchMapping`; el resto lo lee graphql-java del record.

| Tipo.campo | Fuente | Caso de uso | Observación |
| --- | --- | --- | --- |
| `Query.editions` / `edition` | `QueryEditionsUseCase.list` / `get` | existente | `edition` nullable: `NotFoundException` → `null` + error |
| `Query.challenge` | `GetChallengeUseCase` | existente | |
| `Query.team` | `QueryTeamsUseCase.get` | **nuevo (spec 02)** | |
| `Query.round` | `QueryRoundsUseCase.get` | existente | |
| `Query.slot` | `QueryRoundsUseCase.bySlot` | **nuevo método (spec 02)** | el view del slot guarda `roundId`, `teamId` |
| `Query.attempt` | `GetAttemptBreakdownUseCase.execute` | existente | devuelve `AttemptBreakdown`; el view del intento se arma con `attempt()`; el breakdown se guarda en el view para no repetir la carga en `Attempt.breakdown` |
| `Query.appeal` | `QueryAppealsUseCase.get` | existente | |
| `Query.standings` / `Challenge.standings` | `QueryStandingsUseCase.execute(StandingsId)` | existente | `IllegalArgumentException` si la categoría no es de la edición → `BAD_REQUEST` |
| `Edition.challenges` | `ListEditionChallengesUseCase` | existente | |
| `Edition.registrations` | `QueryRegistrationsUseCase.list` + filtro por categoría | existente | validar la categoría con el view de la edición (lista de ids) antes de filtrar; categoría ajena → `IllegalArgumentException` |
| `Registration.category` | del `EditionView` cacheado en el `RegistrationView` (guardar `CategoryView` al construirlo) | — | sin consulta |
| `Registration.team`, `Slot.team`, `Attempt.team`, `Appeal.team`, `StandingsEntry.team` | **`@BatchMapping`** → `QueryTeamsUseCase.byIds(Set<TeamId>)` | **nuevo (spec 02)** | un método por tipo; todos llaman al mismo caso de uso. Un `teamId` guardado que no existe es `InconsistentStateException` (500), no `NOT_FOUND` |
| `Team.results` | `QueryTeamResultsUseCase.execute(TeamId, ChallengeId, Optional<CategoryId>)` | **nuevo (spec 02)** | devuelve `List<TeamTurn(Round, Slot, List<Attempt>)>`; el view de cada intento se construye sin breakdown y `Attempt.breakdown` lo carga si se pide |
| `Challenge.edition` | **`@BatchMapping`** → `QueryEditionsUseCase.byIds(Set<EditionId>)` | **nuevo método (spec 02)** | edición ausente de un desafío guardado → `InconsistentStateException` → `INTERNAL_ERROR` |
| `Challenge.currentRulebook` / `rulebooks` / `rulebook(version)` | del `Challenge` ya cargado: guardar en `ChallengeView` la lista de `RulebookView` | — | `rulebook(version)` filtra la lista; inexistente → `null` sin error (es un filtro, no un id del pedido) |
| `Challenge.rounds` | `QueryRoundsUseCase.list(id, Optional<CategoryId>)` | existente | |
| `Challenge.appeals` | `QueryAppealsUseCase.ofCategory(StandingsId)` | existente | |
| `Round.challenge` | **`@BatchMapping`** → `GetChallengeUseCase.byIds(Set<ChallengeId>)` | **nuevo método (spec 02)** | |
| `Round.category` | **`@BatchMapping`** → `QueryEditionsUseCase.byIds` por `editionId`, después `edition.category(categoryId)` | **nuevo método (spec 02)** | categoría ausente de una edición guardada → `InconsistentStateException` |
| `Slot.turn` | **`@BatchMapping`** → `QueryTurnAttemptsUseCase.ofRounds(Set<RoundId>)` | **nuevo método (spec 02)** | una sola llamada con los `roundId` distintos de los slots (el `SlotView` lo guarda); el caso de uso resuelve el reglamento **una vez por (desafío, categoría)** y carga los intentos de esas rondas en una lectura. Es la razón de agrupar los campos en `Turn` y no dejarlos sueltos en `Slot` (D12) |
| `Attempt.round`, `RoundResult.round` | **`@BatchMapping`** → `QueryRoundsUseCase.byIds(Set<RoundId>)` | **nuevo método (spec 02)** | un `roundId` guardado que no existe es `InconsistentStateException` |
| `Attempt.breakdown` | **`@BatchMapping`** → `GetAttemptBreakdownUseCase.byIds(Set<AttemptId>)` | **nuevo método (spec 02)** | si el view ya trae el breakdown (vino de `Query.attempt` o de un lote de `attempt`), devolverlo sin cargar; los demás se cargan en lote |
| `Attempt.appeals` | `QueryAppealsUseCase.ofAttempt` | existente | lista por padre: una lectura por intento, como `Challenge.rounds` es una por desafío (README, "Estrategia por relación") |
| `RoundResult.attempt`, `Appeal.attempt` | **`@BatchMapping`** → `GetAttemptBreakdownUseCase.byIds(Set<AttemptId>)` | **nuevo método (spec 02)** | devuelve `AttemptBreakdown`: el `AttemptView` se construye **con** el breakdown, así `attempt { breakdown { … } }` no vuelve a consultar |

## Reglas para los `*View`

- Un record por tipo del esquema, en `com.roboleague.api.graphql.view`, con
  `static from(...)` que recibe tipos del dominio o de application. Los nombres
  de los componentes del record **coinciden con los campos del SDL** (graphql-java
  los lee por nombre); cuando un campo se resuelve con `@SchemaMapping`, el
  record **no** tiene ese componente.
- Un view guarda los ids que sus resolvers hijos necesitan (`ChallengeView.editionId`,
  `SlotView.roundId`…) aunque el SDL también los exponga.
- `AttemptView` guarda `Optional<ScoreBreakdownView>`: lleno si el padre ya lo
  cargó (`Query.attempt`, lotes de `RoundResult.attempt`/`Appeal.attempt`), vacío si no
  (`Turn.attempts`, `Team.results`); en ese caso `Attempt.breakdown` lo carga en lote.
- `StandingsEntryView.teamName` sale de `TeamIdentity`, sin consulta; es el
  ejemplo de "campo gratis vs campo que cuesta" para la presentación.
- Nada de Jackson ni Spring en los views: son records planos.
