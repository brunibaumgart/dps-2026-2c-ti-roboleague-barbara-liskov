# RoboLeague

Plataforma para competencias de robótica: torneos, elegibilidad de equipos, programación de rondas, evaluación de intentos, puntaje explicable, rankings con desempate y apelaciones.

## Requisitos

- Java 25
- Maven 3.6.3 o superior
- Docker (Postgres con `docker compose`; los tests de repositorio y de API lo usan vía Testcontainers)

El reloj de la aplicación usa `America/Argentina/Buenos_Aires` por defecto.
Se configura con `ROBOLEAGUE_TIME_ZONE` (un identificador válido de `ZoneId`, por
ejemplo `UTC`); no toma la zona implícita de la máquina. Los timestamps siguen
siendo fechas/horas locales sin offset en los contratos existentes.
Los casos de uso reciben los puertos `Clock` e `IdGenerator`; infrastructure
provee `SystemClock` y `UuidGenerator`. Las fábricas/transiciones del dominio
reciben fechas y metadatos explícitos, y los tests pueden fijar esos valores.
No cambiar a UTC la lectura de un historial existente: los timestamps almacenados
no se reinterpretan al configurar la zona para nuevas operaciones.

Las identidades del núcleo son tipos distintos (`TeamId`, `JudgeId`, `SlotId`,
`RoundId`, `EditionId`, `CategoryId`, `AppealId`, `RankingId`, `ParticipantId`,
`RobotId` y `TrackId`), además de los existentes `ChallengeId` y `AttemptId`.
Controllers y mappers convierten explícitamente entre estos tipos y texto:
los ids de JSON, paths, columnas y claves de mapas JSON siguen siendo strings.
Los autores que pueden tener varios roles usan `ActorId`; no se identifican
automáticamente como jueces. `AttemptId` contiene un `SlotId` y un número
positivo, conserva `<slot>-<numero>` y parsea desde el último guion.

## Correr la demo

Desde la raíz del repo:

```bash
docker compose up -d --wait
mvn -B package -DskipTests
java -jar roboleague-api/target/roboleague-api-1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

El perfil `demo` vacía la base, aplica las migraciones y carga `DemoFixture` a través de los casos de uso. Cada corrida termina en el mismo estado:

- Una edición (`ed-1`, categoría `cat-junior`) con tres desafíos: Laberinto (`ch-maze`), Seguidor de línea (`ch-line`, con su reglamento en v2) y Rescate (`ch-rescue`, mixto: exige mediciones automáticas y panel de jueces). Entre los tres usan los doce tipos de regla (base, bonificaciones y deducciones), la compuesta "Desempeño en pista", penalizaciones, bonificaciones con tope (40 / 30 / 30), mejores N de M y criterios de desempate encadenados. Se consultan con `GET /challenges/{id}`.
- Sobre Laberinto: dos equipos, una ronda, dos intentos (el desglose muestra el recorte del tope), un ranking provisional, una apelación aceptada con recálculo y la publicación oficial.
- Sobre Rescate (F3): una ronda con un intento de CyberTeam que recibió las mediciones y el panel de jueces (147,5) y uno de TitanTeam que tiene las mediciones y espera el panel (`GET /attempts/{id}/breakdown` lo muestra pendiente).

Programar rondas por desafío y ver la tabla con mejores N de M son de los otros frentes; hoy la demo los deja configurados.

Sin el perfil `demo`, la app levanta en `http://localhost:8080` sobre la base tal como esté.

Si el puerto 5432 ya está ocupado (otro Postgres local), elegí otro con `DB_PORT` en los dos comandos: `DB_PORT=5433 docker compose up -d --wait` y `DB_PORT=5433 java -jar ...`.

Para apagar Postgres: `docker compose down` (con `-v` también borra los datos).

## Tests

```bash
mvn -B verify
```

Corre los tres tipos de test. Todos se llaman `*Test`, así que `mvn test` también los corre.

| Tipo | Qué prueba | Con qué |
| --- | --- | --- |
| Unitario | Reglas, agregados, casos de uso | JUnit, Mockito, repositorios en memoria |
| Repositorio | Que un agregado se guarda y vuelve igual | `@DataJpaTest` contra Postgres en Testcontainers |
| API | Rutas, códigos HTTP, JSON y el cableado entero | `@SpringBootTest` + MockMvc sobre la app real (subclases de `ApiTest`) |

La CI (`.github/workflows/ci.yml`) corre `mvn -B verify` en cada PR y en cada push a `main`. Para que bloquee PRs: en GitHub, *Settings → Branches → Branch protection rule* para `main`, con *Require status checks to pass* y el check `build`.

## Módulos

Arquitectura hexagonal: todas las dependencias apuntan hacia adentro.

| Módulo | Contiene | Depende de |
| --- | --- | --- |
| `roboleague-domain` | Agregados, value objects, servicios de dominio y puertos (`*Repository`). Java plano, sin Spring. | nada |
| `roboleague-application` | Casos de uso. Java plano, sin Spring. | domain |
| `roboleague-infrastructure` | Adaptadores de salida: Postgres (entidad JPA propia + mapper + migración Flyway) y repositorios en memoria. | domain |
| `roboleague-api` | Composition root de Spring Boot, controllers REST, DTOs, manejo de errores, `DemoFixture`. | application, infrastructure |

Los paquetes no cambiaron al separar módulos (`com.roboleague.usecase`, `com.roboleague.repository.memory`, …).

## Convenciones de la API

- Recursos en plural; lo que se crea dentro de otro recurso cuelga de él: `POST /editions/{id}/registrations`.
- Las transiciones de estado son subrecursos con `POST`: `/appeals/{id}/review`, `/acceptance`, `/rejection`.
- El controller recibe un `record` de request y devuelve un DTO (`XxxDto.from(agregado)`); el agregado nunca se serializa directo.
- Errores con cuerpo `ErrorDto {error, details}`. `ApiExceptionHandler` traduce lo que lanzan los casos de uso:

  | Excepción | HTTP |
  | --- | --- |
  | `IllegalArgumentException` (datos inválidos, id inexistente) | 400 |
  | `IllegalStateException` (transición no permitida) | 409 |
  | `TeamIneligibleException` | 422, con cada motivo en `details` |
  | Un campo JSON que el cuerpo no tiene (por ejemplo `"penalties"`) | 400 `Unknown field '...'`, con los campos esperados en `details` |
  | Otro pedido guardó lo mismo a la vez (versión vieja o id repetido al confirmar) | 409, para volver a cargar y reintentar |

  Un caso de uso que devuelva un resultado `sealed` se traduce en su controller con un `switch` (por ejemplo `Published → 200`, `Rejected → 409`).
- Cada caso de uso corre en una transacción: si falla a mitad de camino no queda nada guardado (`TransactionalUseCases`).
- Referencia: `AppealController` y `AppealControllerTest`.

### Contratos

| Método | Ruta | Estado |
| --- | --- | --- |
| POST | `/appeals/{id}/review` | hecho |
| POST | `/editions` | hecho |
| POST | `/editions/{id}/challenges` | hecho |
| POST | `/challenges/{id}/rulebook/versions` | hecho |
| GET | `/challenges/{id}` | hecho |
| POST | `/editions/{id}/registrations` | pendiente |
| POST | `/challenges/{id}/rounds` | pendiente |
| PUT | `/attempts/{id}/measurements` | hecho |
| PUT | `/attempts/{id}/judge-scores` | hecho |
| GET | `/attempts/{id}/breakdown` | hecho |
| POST | `/attempts/{id}/appeals` | pendiente |
| POST | `/appeals/{id}/acceptance` · `/rejection` | pendiente |
| GET | `/challenges/{id}/standings[/versions/{v}]` | pendiente |
| POST | `/challenges/{id}/standings/versions/{v}/publication` | pendiente |

### Configurar el evento y los desafíos

`POST /editions` → 201:

```json
{"id": "ed-1", "name": "RoboLeague 2026", "editionNumber": 1,
 "tournament": {"id": "t-1", "name": "RoboLeague", "season": {"id": "s-2026", "year": 2026, "name": "Temporada 2026"}},
 "startDate": "2026-11-10", "endDate": "2026-11-12",
 "categories": [{"id": "cat-junior", "name": "Junior", "minMembers": 2, "maxMembers": 4,
                 "minAge": 12, "maxAge": 17, "maxWeightGrams": 2500}]}
```

`POST /editions/{id}/challenges` con `{"id", "name", "rulebook"}` → 201 con el desafío y su reglamento v1. `POST /challenges/{id}/rulebook/versions` con un `rulebook` → 201 con la versión nueva. Un reglamento con problemas es **422** con cada problema en `details` (`"rule 'Faltas': missing parameter 'deductionPerPenalty'"`). Edición o desafío inexistente: 400; id repetido: 409. Una edición sin un dato obligatorio (id o nombre del torneo, id o nombre de la temporada o de una categoría), con un id o nombre en blanco o con dos categorías del mismo id: 400.

Un reglamento en JSON (lo que se manda es lo que `GET /challenges/{id}` devuelve en `currentRulebook`):

```json
{"metrics": [{"name": "checkpoint", "source": "AUTOMATIC_MEASUREMENTS", "unit": "COUNT",
              "range": {"min": 0, "max": 1}}],
 "scoring": {
   "rules": [
     {"type": "composite", "name": "Desempeño en pista", "rules": [
       {"type": "time", "name": "Tiempo", "numbers": {"basePoints": 100, "targetTimeSeconds": 60,
         "pointsPerSecondUnder": 1.5, "deductionPerSecondOver": 2, "minPoints": 0}},
       {"type": "objectives", "name": "Objetivos", "numbers": {"pointsPerObjective": 20}}]}],
   "bonuses": [
     {"type": "milestone", "name": "Checkpoint", "numbers": {"threshold": 1, "bonus": 30},
      "metrics": {"metric": {"name": "checkpoint", "source": "AUTOMATIC_MEASUREMENTS"}}},
     {"type": "all-objectives", "name": "Todos los objetivos", "numbers": {"totalObjectives": 5, "bonus": 25}}],
   "deductions": [
     {"type": "penalty", "name": "Faltas de pista", "numbers": {"deductionPerPenalty": 15}}],
   "bonusLimit": {"type": "capped", "numbers": {"maximum": 40}}},
 "ranking": {"roundSelection": {"type": "best-n-of-m", "numbers": {"considered": 3, "outOf": 5}},
             "criteria": ["higher-total", "lower-time", "lower-deductions"]}}
```

Cada regla va en la lista de su sección: `rules` (base), `bonuses` o `deductions`. Una regla base o una bonificación nunca resta y una deducción nunca suma; el tope (`bonusLimit`) recorta la suma de las bonificaciones y no toca las deducciones.

| Sección | `type` | `numbers` | `metrics` |
| --- | --- | --- | --- |
| base | `time` | `basePoints`, `targetTimeSeconds`, `pointsPerSecondUnder`, `deductionPerSecondOver`, `minPoints` | — |
| base | `objectives` | `pointsPerObjective` | — |
| base | `judges` | `weightMultiplier` | — |
| base | `precision` | `maxPoints` | `accuracy` |
| base | `victims` | `pointsPerRescued` | `rescued` |
| base | `composite` | — | — (lleva `rules`, solo reglas base) |
| bonificación | `milestone` | `threshold`, `bonus` | `metric` |
| bonificación | `all-objectives` | `totalObjectives`, `bonus` | — |
| deducción | `penalty` | `deductionPerPenalty` | — |
| deducción | `counted-fault` | `freeAllowance`, `deductionPerFault` | `faults` |
| deducción | `resource-consumption` | `maxAllowedConsumption`, `penaltyPerExcessUnit` | — |
| deducción | `abandoned-victims` | `totalVictims`, `deductionPerAbandoned` | `rescued` |

Un tipo en la lista de otra sección es 422 y dice a cuál pertenece (`"rule 'Faltas': type 'penalty' is a deduction, not a bonus"`). Un parámetro, una métrica o un número que la pieza no usa también es 422 (`"unknown parameter 'deductionPerPenaltyy'"`): no se ignora en silencio.

Una métrica es `{"name", "source"}` con `source` = `AUTOMATIC_MEASUREMENTS` o `JUDGE_PANEL`. Toda métrica que lee una regla tiene que estar declarada en `metrics` con la misma fuente, una unidad (`COUNT`, `SECONDS`, `METERS`, `RATIO` o `POINTS`; `COUNT` solo acepta enteros) y un rango (`min` y, si tiene tope, `max`). Una métrica no declarada o de otra fuente, un rango invertido o una métrica declarada dos veces: 422. Una unidad desconocida: 400. Tiempo, objetivos, faltas, consumo y notas de jueces son campos fijos y no se declaran. Límite de bonificaciones: `capped` (`maximum`) o `unlimited`. Selección de rondas: `best-n-of-m` (`considered`, `outOf`) o `all-rounds`. Criterios: `higher-total`, `lower-time`, `lower-deductions` (menos puntos descontados por las deducciones), `higher-judge-score`. El primero es siempre `higher-total` (se ordena por puntaje) y ninguno se repite; los demás desempatan en el orden declarado.

### Cargar los resultados de un intento

El id de un intento es su turno: el slot y el número de intento (`<slotId>-<n>`, por ejemplo `slot-7-1`). El turno tiene que estar en una ronda programada, el juez que carga tiene que estar asignado al slot y el equipo es el del slot. El primer resultado abre el intento con la versión vigente del reglamento del desafío; los siguientes se puntúan con esa misma versión aunque se publique otra.

Cada fuente llega por separado (F3). El reglamento dice qué fuentes exige: si todas sus reglas leen sensores alcanza con las mediciones; el desafío mixto espera también al panel de jueces y queda `AWAITING_SOURCES`, sin puntaje, hasta que llega la última.

`PUT /attempts/{id}/measurements` → 200:

```json
{"challengeId": "ch-maze", "judgeId": "j-1", "timeSeconds": 50, "objectives": 4, "penalties": 0,
 "consumption": 70, "measurements": {"colisiones": 1, "checkpoint": 1, "vueltas": 1}}
```

`PUT /attempts/{id}/judge-scores` → 200:

```json
{"challengeId": "ch-rescue", "judgeId": "j-2", "scores": {"j-1": 8, "j-2": 7},
 "measurements": {"victimas_rescatadas": 3, "rescate_completo": 0}}
```

`measurements` son las métricas con nombre que el reglamento declara para esa fuente; tiempo, objetivos, faltas, consumo y notas de jueces son campos fijos. Las dos responden el intento:

```json
{"id": "slot-7-1", "slotId": "slot-7", "roundId": "…", "teamId": "t-a", "challengeId": "ch-rescue",
 "rulebookVersion": 1, "status": "AWAITING_SOURCES", "received": ["AUTOMATIC_MEASUREMENTS"], "score": null}
```

`status` es `SCHEDULED`, `AWAITING_SOURCES`, `EVALUATED`, `UNDER_APPEAL`, `ADJUSTED` o `DISQUALIFIED`; `score` es el puntaje que cuenta para la tabla, `null` mientras espera una fuente o si está descalificado.

| Caso | HTTP |
| --- | --- |
| Mediciones que no cumplen el reglamento (faltan, sobran, fuera de rango), una fuente que el reglamento no toma, un juez no asignado al slot | 422, con cada problema en `details` |
| La misma fuente dos veces, un intento ya puntuado (las correcciones van por ajuste o apelación), un intento de otro desafío | 409 |
| Un slot que no está en ninguna ronda, un id sin número, un campo obligatorio que falta, un valor negativo | 400 |

`GET /attempts/{id}/breakdown` → 200 con lo que el intento todavía espera (`awaiting`), su puntaje, cada ítem de la última revisión (tope y piso incluidos), lo que aportó cada fuente (`bySource`) y cada revisión con su versión de reglamento, autor y motivo.

## Inscripción y cambios de equipo (casos de uso)

La edición contiene `Registration`, identificada por edición/equipo, con la
categoría, la fecha de inicio de edición para calcular edades y `registeredAt`
obtenido del reloj. Un equipo puede competir en categorías distintas en otras
ediciones. `TeamRepository` es su fuente canónica; la edición conserva TeamId,
sin referencias mutables al equipo.

`RegisterTeamUseCase.execute(editionId, categoryId, team)` crea una inscripción
para un equipo nuevo. Para un equipo existente, usar la variante con TeamId;
enviar otro Team con su mismo id se rechaza para impedir sobrescrituras.
Se comprueban categoría ofrecida, edades, cantidad de miembros, robot y
documentación; una reinscripción en la misma edición es conflicto.

Team, Robot, Documentation y Edition son inmutables. Construir el candidato
completo y guardar cambios mediante `UpdateTeamUseCase.execute(teamId, candidate)`:
valida todas sus inscripciones antes de reemplazar el equipo. Para cambiar
categoría, usar `ChangeRegistrationCategoryUseCase.execute(editionId, teamId,
categoryId)`, que afecta esa edición y conserva sus tiempos. Cambiar un documento
con `withDocument` invalida la verificación del candidato; `verify` devuelve
otro valor con autor y hora explícitos. Conservar siempre los valores devueltos.

Programación revalida participantes; programación y rankings resuelven el
estado actual desde TeamRepository. Rechazos de validación no dejan cambios
parciales en memoria. Team/Edition/Registration todavía no tienen persistencia
Postgres: sus datos se pierden al reiniciar y no hay rollback en memoria ante
fallos técnicos entre escrituras. Su JPA corresponde al frente 5; los endpoints
HTTP de estos flujos se implementan en la spec 05.

## Cómo sumar lo tuyo

**Un endpoint.** Controller en `roboleague-api/src/main/java/com/roboleague/api/<contexto>/`, con su DTO. Si el caso de uso es nuevo, su `@Bean` va en `UseCaseConfig`. El test extiende `ApiTest` y usa ids propios (el contexto y la base se comparten entre clases de test).

**Un tipo de regla nuevo.** Clase en `roboleague-domain/.../evaluation/rules/` que implementa la interfaz de su sección (`BaseRule`, `BonusRule` o `DeductionRule`), con `TYPE`, constantes para los nombres de sus parámetros, `static from(RuleDefinition)` y `definition()` (los dos usan las mismas constantes), y `metrics()` con las métricas con nombre que lee. Un registro en la sección que le corresponde en `RuleCatalog.standard()`, un caso en `RuleCatalogTest.everyRuleType()` y en `ScoreRuleSectionsTest`, y una fila en la tabla de "Configurar el evento y los desafíos". Las reglas existentes no se tocan.

**Validar una captura contra el reglamento.** `rulebook.check(fuente, mediciones)` revisa lo que mandó una fuente: que estén todas las métricas declaradas para ella, ninguna de más, cada una en su unidad y rango. Devuelve `MeasurementCheck.Accepted` o `Rejected(problemas)`. `Attempt.receive` lo llama con cada fuente que llega y no cambia nada si la rechaza; `ReceiveResultUseCase` lo traduce a 422.

**Leer el puntaje de un intento.** `attempt.countableScore()` es el desglose que cuenta para la tabla: vacío mientras espera una fuente o si está descalificado, nunca un cero de relleno. Cada revisión (`getRevisionHistory()`) guarda la versión de reglamento que la puntuó.

**Persistencia de un agregado.** Seguir el caso de `Appeal` en `roboleague-infrastructure/.../repository/jpa/`:

1. Migración nueva en `src/main/resources/db/migration/V<n>__<descripcion>.sql`. Nunca editar una migración ya mergeada.
2. `XxxJpaEntity` (package-private, solo campos) y `SpringDataXxx extends JpaRepository`.
3. `XxxMapper` en los dos sentidos. Para volver al dominio, el agregado expone `Xxx.restore(...)`: así no necesita constructor vacío ni setters.
4. `JpaXxxRepository implements XxxRepository`, anotado `@Repository`.
5. Borrar el bean en memoria de `InMemoryRepositoryConfig` (si quedan los dos, la app no arranca).
6. `JpaXxxRepositoryTest` con `@DataJpaTest` e `@Import({PostgresContainer.class, JpaXxxRepository.class})`.

## Más

- [`DESIGN.md`](DESIGN.md): contextos, decisiones de diseño, patrones y alternativas descartadas.
