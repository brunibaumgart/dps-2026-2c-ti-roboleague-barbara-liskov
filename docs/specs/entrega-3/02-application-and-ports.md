# 02 — Casos de uso de lectura y puerto para el lote

Estado: **implementada** (PR #23, DESIGN 2.33). Fase 1 del README. No depende de GraphQL: todo lo de
esta spec es Java plano en `roboleague-domain` (un método de puerto),
`roboleague-infrastructure` (dos adaptadores) y `roboleague-application`
(dos casos de uso y un método). Se verifica sin Docker salvo el adaptador JPA.

## Por qué hacen falta

GraphQL navega relaciones que REST no ofrece como recurso: "un equipo por id",
"los resultados de un equipo en un desafío", "un turno por id", "muchos equipos
de una vez". La regla del proyecto es que los adaptadores de entrada llamen a
casos de uso, nunca a repositorios (`roboleague-api/AGENTS.md`). Por eso las
lecturas nuevas se agregan en application, con el mismo estilo que
`QueryRoundsUseCase`, y el lote entra por el puerto del dominio.

## 1. `findByIds` en cinco puertos y `findByRoundIds` (domain + infrastructure)

El mismo método en `TeamRepository`, `AttemptRepository`, `RoundRepository`,
`ChallengeRepository` y `EditionRepository`. Se describe para `Team`; los otros
son idénticos cambiando el tipo.

```java
// roboleague-domain/.../repository/TeamRepository.java
/** The teams with those ids, by TeamId; ids that do not exist are simply absent. */
List<Team> findByIds(Collection<TeamId> ids);
```

- `JpaTeamRepository`: `jpa.findAllById(ids.stream().map(TeamId::value).toList())`,
  mapeado con `TeamMapper::toDomain` y ordenado por id (`findAll` ya ordena así).
  Con `ids` vacío, devolver `List.of()` sin ir a la base.
- `InMemoryTeamRepository`: filtrar `storage`, mismo orden.
- Tests: `JpaTeamRepositoryTest` (existe) suma un caso "devuelve solo los ids
  pedidos que existen, ordenados, y lista vacía para ids vacíos"; el test en
  memoria equivalente va en `roboleague-infrastructure/src/test/.../memory/`
  (crear `InMemoryTeamRepositoryTest` si no existe, siguiendo
  `InMemoryRoundRepositoryTest`).
- No tocar `TeamJpaEntity` ni crear migración: es una consulta por clave primaria.
- `AttemptRepository.findByIds(Collection<AttemptId>)`: `JpaAttemptRepository` usa
  `jpa.findAllById` y pasa cada entidad por su `toDomain` privado (registra la
  versión cargada en `loadedVersions`, igual que `findById`). Orden por id.
  `InMemoryAttemptRepository`: filtrar `storage`. Test en `JpaAttemptRepositoryTest`.
- `RoundRepository.findByIds(Collection<RoundId>)`: `JpaRoundRepository` con
  `jpa.findAllById` + `RoundMapper::toDomain`, orden por id. `InMemoryRoundRepository`:
  filtrar. Tests en `JpaRoundRepositoryTest` e `InMemoryRoundRepositoryTest` (existen).
- `ChallengeRepository.findByIds(Collection<ChallengeId>)` y
  `EditionRepository.findByIds(Collection<EditionId>)`: igual, con
  `ChallengeMapper`/`EditionMapper`; tests en `JpaChallengeRepositoryTest`,
  `JpaEditionRepositoryTest` e `InMemoryChallengeRepositoryTest` (existen).
- `AttemptRepository.findByRoundIds(Collection<RoundId>)`: `SpringDataAttempts`
  gana `List<AttemptJpaEntity> findByRoundIdIn(Collection<String>)`; orden por
  id de intento. En memoria, filtrar. Lo usa `CategoryRulebook` (§6) para que el
  reglamento vigente de una categoría cueste **una** lectura de intentos y no una
  por ronda; eso también acelera `GET /slots/{id}/results`, el recálculo y la
  publicación, que pasan por el mismo servicio.
- Los cinco: `ids` vacío → `List.of()` sin ir a la base; ids repetidos se piden
  una vez (deduplicar antes de llamar a JPA).

## 2. `QueryTeamsUseCase` (application)

```java
public class QueryTeamsUseCase {
    public QueryTeamsUseCase(TeamRepository teams)
    /** The canonical team, or NotFoundException("Team", id). */
    public Team get(TeamId id)
    /** The teams with those ids, keyed by id. A stored reference that does not exist is an
     *  InconsistentStateException: the caller got the ids from registrations, slots or standings. */
    public Map<TeamId, Team> byIds(Collection<TeamId> ids)
}
```

- `byIds` deduplica, llama una sola vez a `findByIds`, y si falta alguno lanza
  `InconsistentStateException("Registered team not found: " + faltantes)`,
  igual que `CategoryResultsReader.identity`.
- Test `QueryTeamsUseCaseTest` con `InMemoryTeamRepository`: `get` ok y 404;
  `byIds` con repetidos devuelve cada equipo una vez; un id ausente lanza
  `InconsistentStateException` con el id en el mensaje; colección vacía devuelve
  mapa vacío sin fallar.

## 3. `QueryTeamResultsUseCase` (application)

La consulta de la consigna: equipo → resultados por ronda → intentos. Carga en
un número fijo de consultas, sin importar cuántas rondas o intentos haya.

```java
public class QueryTeamResultsUseCase {
    public QueryTeamResultsUseCase(TeamRepository teams, ChallengeRepository challenges,
                                   EditionRepository editions, RoundRepository rounds,
                                   AttemptRepository attempts)
    /** Every turn the team has in the challenge (optionally in one category), in round order,
     *  with its attempts in the order they were opened. */
    public List<TeamTurn> execute(TeamId teamId, ChallengeId challengeId, Optional<CategoryId> category)
}

/** One turn of a team: its round, its slot and the attempts it ran there (none yet is valid). */
public record TeamTurn(Round round, Slot slot, List<Attempt> attempts) { /* copias defensivas, requireNonNull */ }
```

Algoritmo (y número de lecturas):

1. `teams.findById` → `NotFoundException("Team", id)` si no existe. (1)
2. `challenges.findById` → `NotFoundException("Challenge", id)`. (1)
3. Si viene categoría: `editions.findById(challenge.getEditionId())` (ausente →
   `InconsistentStateException`) y `edition.category(categoryId)`, que lanza
   `IllegalArgumentException` si la edición no la ofrece (→ `BAD_REQUEST`). (0 o 1)
4. `rounds.findByChallengeId(challengeId)`, filtradas por categoría si vino, y
   quedarse con las que tienen un slot del equipo (`round.getSlots()` con
   `getTeamId().equals(teamId)`). Ordenar por `getRoundNumber`, luego
   `getCategoryId().value()`, luego `getId().value()` (igual que `QueryRoundsUseCase.list`). (1)
5. `attempts.findByTeamId(teamId)`, filtrados a los `roundId` del paso 4 y
   agrupados por `getSlotId()`; dentro del turno, ordenados por `getId().number()`. (1)
6. Armar un `TeamTurn` por slot del equipo, con lista vacía si no corrió.

Total: 4 o 5 lecturas, constantes. Anotar este número en la javadoc: es parte
de la evidencia del N+1 "evitado por diseño" (README D5).

Test `QueryTeamResultsUseCaseTest` (repositorios en memoria; construir el
escenario como `RecalculateStandingsUseCaseTest` o `StandingsUseCasesTest`):
- dos rondas de un desafío con turno del equipo, una con dos intentos (usar un
  reglamento con `perTurn` 2, ver `AttemptSelectionTest`/`TurnAttemptsTest`) y
  otra sin resultado → dos `TeamTurn`, el primero con dos intentos en orden
  1, 2 y el segundo con lista vacía;
- una ronda de otra categoría se excluye con el filtro y se incluye sin él;
- una ronda del mismo desafío donde el equipo no tiene turno no aparece;
- equipo inexistente → `NotFoundException` con `resource() == "Team"`;
  desafío inexistente → `"Challenge"`; categoría que la edición no ofrece →
  `IllegalArgumentException`;
- no escribe: después de ejecutar, los repositorios tienen lo mismo que antes.

## 4. `QueryRoundsUseCase.bySlot` y `byIds` (application)

```java
/** The round that contains the slot, or NotFoundException("Slot", slotId). */
public Round bySlot(SlotId slotId)
/** The rounds with those ids, keyed by id. A stored reference that does not exist is an
 *  InconsistentStateException: the caller got the ids from attempts or standings. */
public Map<RoundId, Round> byIds(Collection<RoundId> ids)
```

`bySlot` usa `rounds.findBySlotId`. `byIds` es igual que `QueryTeamsUseCase.byIds`
(deduplica, una llamada a `findByIds`, faltante → `InconsistentStateException`).
Agregar los casos a `QueryRoundsUseCaseTest` si existe o crearlo (ok, 404,
lote con repetidos, lote con faltante, lote vacío).

## 5. `GetAttemptBreakdownUseCase.byIds` (application)

```java
/** The explained attempts with those ids, keyed by id, in two reads: the attempts and their challenges.
 *  A stored reference that does not exist is an InconsistentStateException. */
public Map<AttemptId, AttemptBreakdown> byIds(Collection<AttemptId> ids)
```

- `attempts.findByIds` una vez; `challenges.findByIds` con los
  `getRulebookReference().challengeId()` distintos, **una** vez; por intento,
  `challenge.rulebook(version)` y el mismo `new AttemptBreakdown(...)` que `execute`.
  Extraer ese armado a un método privado `explain(attempt, rulebook)` que `execute`
  también use (regla #27). Dos lecturas, sea cual sea el número de intentos o desafíos.
- Rulebook ausente → `InconsistentStateException`, como en `execute`.
- Test en `GetAttemptBreakdownUseCaseTest` (existe o crear): dos intentos del
  mismo desafío con versiones distintas del reglamento → cada uno explicado con
  la suya; id faltante → `InconsistentStateException`; vacío → mapa vacío.

## 6. `CategoryRulebook` en una lectura, y `QueryTurnAttemptsUseCase.ofRounds` (application)

Hoy `CategoryRulebook.attemptsOf` hace un `findByRoundId` por ronda de la
categoría (`CategoryRulebook.java`, método `attemptsOf`): el reglamento vigente
de una categoría con R rondas cuesta 1 + R lecturas. Cambiarlo a
`attempts.findByRoundIds(ids de las rondas)` y ordenar en memoria igual que
ahora (por ronda, slot y número). Mismo resultado, una lectura. Los tests de
`CategoryRulebook`/`StandingsUseCasesTest` no cambian de aserciones.

```java
/** Every slot of those rounds as the capture sees it, keyed by slot id. Reads: the rounds (1), their challenges
 *  (1) and, per distinct (challenge, category), the category's rounds (1) and attempts (1). */
public Map<SlotId, TurnResults> ofRounds(Collection<RoundId> roundIds)
```

- `rounds.findByIds` (faltante → `InconsistentStateException`: los ids vienen de
  slots ya cargados); `challenges.findByIds` con los desafíos distintos;
  agrupar las rondas por `(challengeId, categoryId)`; por grupo,
  `categoryRulebook.roundsOf` + `attemptsOf` + `inForce` **una vez** (con el
  cambio de arriba son 2 lecturas por grupo), y los intentos de cada ronda
  pedida se sacan de esa misma lista filtrando por `roundId`, sin volver al
  repositorio.
- Por slot: `TurnAttempts` con los intentos del slot (ver cómo lo arma
  `StoredTurn.of`; si solo acepta `(repository, round, slotId)`, agregar una
  fábrica que reciba la lista ya cargada) y el mismo cálculo de `acceptsResult`
  que `execute`. `execute(slotId)` queda como `findBySlotId` +
  `ofRounds(List.of(round.getId())).get(slotId)`, para no tener dos versiones de
  la misma lógica (regla #27).
- Anotar en la javadoc el número de lecturas: para una categoría, **4**, sin
  importar cuántas rondas o slots se pidan. Es la constante que el test de
  `turn` de la spec 03 §5 afirma.
- Test: dos rondas de la misma categoría, una con tres turnos (uno cancelado,
  uno con dos intentos) → cinco entradas, `acceptsResult` falso solo en el
  cancelado, intentos en orden; una ronda de otra categoría en la misma llamada
  → su reglamento se resuelve aparte; id inexistente → `InconsistentStateException`;
  vacío → mapa vacío.

## 6b. `GetChallengeUseCase.byIds` y `QueryEditionsUseCase.byIds` (application)

Misma forma que `QueryTeamsUseCase.byIds` (deduplicar, una llamada a
`findByIds`, faltante → `InconsistentStateException`), devolviendo
`Map<ChallengeId, Challenge>` y `Map<EditionId, Edition>`. Tests con los
mismos tres casos (repetidos, faltante, vacío) en sus clases de test.

## 7. Cableado

`UseCaseConfig` (api): un `@Bean` por caso de uso nuevo, en el mismo estilo.
`TransactionalUseCases` los envuelve solos porque están en
`com.roboleague.usecase`: no cambiar paquete ni declararlos `final`.

## Verificación de la fase

```bash
mvn -B -pl roboleague-domain test                     # compila el puerto
mvn -B -pl roboleague-infrastructure -am test         # Docker: JpaTeamRepositoryTest
mvn -B -pl roboleague-application -am test            # casos de uso nuevos; cobertura ≥ 90 % líneas / 89 % ramas
```

Si la cobertura de application cae por debajo del mínimo, faltan casos de
rechazo en los tests nuevos; no bajar el umbral del `pom.xml`.

## Fuera de alcance

Lotes para las listas por padre (`ChallengeRepository.findByEditionIds`,
`RoundRepository.findByChallengeIds`, `AppealRepository.findByAttemptIds`…):
una consulta de la demo tiene 1 a 3 padres de cada tipo. Se documentan como
deuda en `DESIGN.md` cap. 8 con este mismo diseño como camino.
