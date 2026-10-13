# RoboLeague

Plataforma para competencias de robótica: torneos, elegibilidad de equipos, programación de rondas, evaluación de intentos, puntaje explicable, rankings con desempate y apelaciones.

## Requisitos

- JDK 25: `java -version` y `mvn -version` tienen que mostrar 25 (el `pom.xml` compila con `maven.compiler.release` 25 y un JDK anterior no lo compila). Si hay varios JDK instalados, apuntar `JAVA_HOME` al 25 o, con SDKMAN, elegir uno de `sdk list java` y activarlo con `sdk use java <versión-25>`.
- Maven 3.6.3 o superior
- Docker (Postgres con `docker compose`; los tests de repositorio y de API lo usan vía Testcontainers)
- Node 20 o superior, solo para los tests de módulos JS del front (opcional)

## Compilar, probar y ejecutar

Desde la raíz del repo:

```bash
mvn -B verify                                    # compila y corre todos los tests; necesita Docker (ver "Tests")
mvn -B package -DskipTests                       # solo genera el jar, sin acreditar pruebas
docker compose up -d --wait                      # Postgres local en el puerto 5432
java -jar roboleague-api/target/roboleague-api-1.0-SNAPSHOT.jar   # API y web en http://localhost:8080
```

Sin perfil, la app usa la base tal como esté (Flyway aplica las migraciones pendientes). Para cargar el escenario de ejemplo, ver "Correr la demo".

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
`RoundId`, `EditionId`, `CategoryId`, `AppealId`, `StandingsId`, `ParticipantId`,
`RobotId` y `TrackId`), además de los existentes `ChallengeId` y `AttemptId`.
`StandingsId` se deriva del desafío y la categoría.
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

El perfil `demo` vacía la base, aplica las migraciones y carga `DemoFixture` a través de los casos de uso. En este perfil el reloj y los ids son de la demo: los ids generados son secuenciales (`demo-1`, `demo-2`...) y el reloj arranca el 9/11/2026 a las 10:00, avanza un segundo por lectura y la carga lo adelanta a cada paso de la historia. Por eso **cada corrida termina en el mismo estado, con los mismos ids, horas y puntajes** (`DemoFixtureTest` carga la demo dos veces sobre una base vacía y lo compara). Después de la carga el reloj sigue avanzando desde ahí (10/11/2026, 14:34): lo que hagas desde la web queda fechado a continuación, no con la hora real.

- Una edición (`ed-1`, categoría `cat-junior`, desde 13 años cumplidos al inicio de la edición, sin edad máxima, del 10 al 12/11/2026) con tres desafíos: Laberinto (`ch-maze`), Seguidor de línea (`ch-line`, con su reglamento en v2 y sin rondas) y Rescate (`ch-rescue`, mixto: exige mediciones automáticas y panel de jueces). Entre los tres usan los doce tipos de regla del catálogo (base, bonificaciones y deducciones), la compuesta "Desempeño en pista", penalizaciones, bonificaciones con tope (40 / 30 / 30), mejores N de M y criterios de desempate encadenados. Se consultan con `GET /challenges/{id}`.
- Tres equipos inscriptos el 9/11: CyberTeam (`t-a`), TitanTeam (`t-b`) y NovaTeam (`t-c`).
- **Laberinto** (mejores 3 de 5 rondas; desempate: mayor puntaje total → menor tiempo → menor descuento por penalizaciones). Cuatro rondas el 10/11, una por hora desde las 9:00. NovaTeam no se presentó a la ronda 4: su turno quedó cancelado (`El equipo no se presentó`), así que no bloquea la publicación.

  | Ronda | CyberTeam | TitanTeam | NovaTeam |
  | --- | --- | --- | --- |
  | 1 (`demo-1`) | 235 | 232 | 220 |
  | 2 (`demo-5`) | 229 | 197,5; 257,5 tras la apelación | 250,5 |
  | 3 (`demo-9`) | 253 | 227,5 | 246,5 |
  | 4 (`demo-13`) | 186,5 (descartada) | 208 (descartada en la v2) | turno cancelado |

  - **v1, provisional** (13:00): 1.º CyberTeam 717; 2.º NovaTeam 717, "Empata en puntaje con el puesto 1; desempata por Menor descuento por penalizaciones" (16 puntos descontados contra 2, con el mismo mejor tiempo de 50 s); 3.º TitanTeam 667,5, que descarta la ronda 2 (cuatro faltas).
  - **Apelación `demo-43`** de TitanTeam sobre el intento `demo-7-1` (ronda 2): presentada a las 13:10, aceptada a las 13:30 sin las faltas. El recálculo da la **v2** (13:30:01): los tres suman 717. 1.º TitanTeam (mejor tiempo 45 s); 2.º CyberTeam, "Empata en puntaje con el puesto 1; desempata por Menor tiempo"; 3.º NovaTeam, "Empata en puntaje con el puesto 2; desempata por Menor descuento por penalizaciones". TitanTeam ahora descarta la ronda 4; CyberTeam también; NovaTeam no descarta nada (corrió tres rondas).
  - La v2 se publica como oficial a las 13:45 (`org-1`). En el desglose de los intentos se ve el recorte del tope de bonificaciones.
- **Rescate** (F3): ronda `demo-17` a las 14:00. CyberTeam (intento `demo-18-1`) recibió las mediciones y el panel de jueces (147,5); TitanTeam (`demo-19-1`) tiene las mediciones y espera el panel (`GET /attempts/demo-19-1/breakdown` lo muestra pendiente); el turno de NovaTeam (`demo-20`) no tiene resultado.

Para recorrerla por HTTP:

```bash
curl -s 'localhost:8080/challenges/ch-maze/standings?categoryId=cat-junior'            # v2 oficial: discarded y explanation
curl -s 'localhost:8080/challenges/ch-maze/standings/versions/1?categoryId=cat-junior' # v1 provisional
curl -s localhost:8080/attempts/demo-7-1/breakdown   # intento apelado, con sus dos revisiones
curl -s localhost:8080/appeals/demo-43
curl -s localhost:8080/rounds/demo-13                # ronda 4, con el turno cancelado
```

Los turnos de Laberinto siguen el orden de inscripción: en la ronda `demo-1` son `demo-2` (CyberTeam), `demo-3` (TitanTeam) y `demo-4` (NovaTeam), y así en las siguientes; el intento de cada turno es `<turno>-1`.

La web permite inscribir equipos, programar rondas por desafío, capturar fuentes, consultar desgloses, presentar y resolver apelaciones, y consultar, recalcular y publicar la tabla por desafío y categoría.

Abrí **http://localhost:8080/** para usar el frontend. HTML/CSS y módulos
JavaScript se sirven desde el mismo jar y puerto; no se requiere npm, servidor
frontend separado, CDN ni configuración CORS. La interfaz usa colores neutros,
formularios con labels, foco visible y layout para escritorio. El header reúne
Equipos, Rondas y turnos, Resultados y Recargar datos. El botón de menú abre
un panel lateral izquierdo con Desglose e historial, Apelaciones y Tabla de
posiciones; se cierra al elegir una sección, al volver a pulsar el botón de menú
o con Escape. Al cambiar de sección, la página vuelve al inicio.

Sin el perfil `demo`, la app levanta en `http://localhost:8080` sobre la base tal
como esté. Si no hay ediciones, muestra un estado vacío; no carga ni borra datos
al abrir la página. Para explorar los tres desafíos, usar el perfil demo solo
sobre una base dedicada: **el inicio de demo limpia esa base**.

Si el puerto 5432 ya está ocupado (otro Postgres local), elegí otro con `DB_PORT` en los dos comandos: `DB_PORT=5433 docker compose up -d --wait` y `DB_PORT=5433 java -jar ...`.

Para apagar Postgres: `docker compose down` (con `-v` también borra los datos).

## Tests

```bash
mvn -B verify
```

Corre los tres tipos de test. Todos se llaman `*Test`, así que `mvn test` también los corre. Para un módulo: `mvn -B -pl roboleague-domain test` (sin Docker) o `mvn -B -pl roboleague-application -am test`; infraestructura y API necesitan Docker.

| Tipo | Qué prueba | Con qué |
| --- | --- | --- |
| Unitario | Reglas, agregados, casos de uso | JUnit 5 (tests parametrizados), AssertJ, repositorios en memoria |
| Repositorio | Que un agregado se guarda y vuelve igual | `@DataJpaTest` contra Postgres en Testcontainers |
| API | Rutas, códigos HTTP, JSON y el cableado entero | `@SpringBootTest` + MockMvc sobre la app real (subclases de `ApiTest`) |

Cada test vive en el mismo módulo y paquete que la clase que prueba (`evaluation/rules/RuleInvariantsTest` prueba `evaluation/rules`). Las invariantes de los value objects se prueban con tablas `@ParameterizedTest` que comprueban el tipo **y** el mensaje de cada rechazo.

### Cobertura

`mvn -B verify` mide cobertura con JaCoCo y deja los reportes HTML en:

| Reporte | Dónde |
| --- | --- |
| Por módulo (solo sus propios tests) | `<módulo>/target/site/jacoco/index.html` |
| Agregado (los cuatro módulos, contando también lo que ejercitan los tests de API) | `roboleague-api/target/site/jacoco-aggregate/index.html` |

El build falla si los tests propios de `roboleague-domain` o `roboleague-application` bajan del mínimo de líneas y ramas fijado en su `pom.xml` (`coverage.line.minimum` / `coverage.branch.minimum`, un par de puntos por debajo de lo medido). En la CI, el reporte agregado queda como artefacto `coverage-report` de cada corrida. `-Djacoco.skip` lo desactiva en una corrida local.

La CI (`.github/workflows/ci.yml`, job `build`, JDK 25 Temurin) corre `mvn -B verify` en cada PR y en cada push a `main`, sube el reporte de cobertura y después corre los tests de módulos JS del front con Node. Si un test o un mínimo de cobertura falla, el job falla. Para que bloquee PRs: en GitHub, *Settings → Branches → Branch protection rule* para `main`, con *Require status checks to pass* y el check `build`.

## Módulos

Arquitectura hexagonal: todas las dependencias apuntan hacia adentro.

| Módulo | Contiene | Depende de |
| --- | --- | --- |
| `roboleague-domain` | Agregados, value objects, servicios de dominio y puertos (`*Repository`). Java plano, sin Spring. | nada |
| `roboleague-application` | Casos de uso. Java plano, sin Spring. | domain |
| `roboleague-infrastructure` | Adaptadores de salida: Postgres para los siete agregados (entidad JPA propia + mapper + migración Flyway) y repositorios en memoria para tests de casos de uso. | domain |
| `roboleague-api` | Composition root de Spring Boot, controllers REST, DTOs, manejo de errores, `DemoFixture`. | application, infrastructure |

Los paquetes no cambiaron al separar módulos (`com.roboleague.usecase`, `com.roboleague.repository.memory`, …).

## Convenciones de la API

- Recursos en plural; lo que se crea dentro de otro recurso cuelga de él: `POST /editions/{id}/registrations`.
- Las transiciones de estado son subrecursos con `POST`: `/appeals/{id}/review`, `/acceptance`, `/rejection`.
- El controller recibe un `record` de request y devuelve un DTO (`XxxDto.from(agregado)`); el agregado nunca se serializa directo.
- Errores con cuerpo `ErrorDto {error, details}`, también los de Spring MVC. `ApiExceptionHandler` traduce lo que lanzan los casos de uso:

  | Excepción | HTTP |
  | --- | --- |
  | `IllegalArgumentException` (dato mal formado: id en blanco o de más de 64 caracteres, nombre de desafío de más de 120, número inválido) | 400 |
  | Falta un parámetro de query (`categoryId`) o un parámetro de ruta no tiene el tipo esperado (`versions/abc`) | 400, nombrando el parámetro |
  | Un campo JSON que el cuerpo no tiene (por ejemplo `"penalties"`) | 400 `Unknown field '<ruta>'`, con los campos esperados en `details`; un valor de otro tipo es 400 `Invalid value for field '<ruta>'` |
  | `NotFoundException` (un id del pedido no existe) | 404 |
  | `ConflictException` (id repetido, revisor que no tomó la apelación) e `IllegalStateException` (transición no permitida) | 409 |
  | `ConcurrentModificationConflict` (otro pedido guardó el mismo agregado antes: versión vieja o alta repetida) | 409, para volver a cargar y reintentar |
  | `BusinessRuleViolation` (por ejemplo, apelar un intento de otro equipo) | 422 |
  | `TeamIneligibleException` | 422, con cada motivo en `details` |
  | `CorruptDataException` (dato guardado irreconstruible), `InconsistentStateException` (referencia rota entre agregados), `DataIntegrityViolationException` o cualquier error inesperado | 500 con mensaje genérico; la causa queda en el log |
  | Base de datos no disponible | 503 |

  Un caso de uso que devuelva un resultado `sealed` se traduce en su controller con un `switch`: reglamento `Published → 201` / `Rejected → 422`; captura `Received → 200` / `Rejected → 422`; aceptación de apelación `Accepted → 200` / `Invalid → 422`; publicación de la tabla `Published → 200` / `Blocked → 409`. En todos los rechazos cada motivo va en `details`.
- Cada caso de uso corre en una transacción (`TransactionalUseCases`) que cubre todos los repositorios: todos los agregados se guardan en Postgres. Los repositorios en memoria solo se usan en tests de casos de uso y no ofrecen rollback.
- Referencia: `AppealController` y `AppealControllerTest`.

### Contratos

Todas las respuestas de error usan `ErrorDto {error, details}`. Además de los códigos de cada fila, cualquier escritura puede responder **409** si otro pedido guardó el mismo agregado antes (`ConcurrentModificationConflict`), y cualquier ruta **500** (dato guardado irreconstruible, referencia rota o error inesperado, con mensaje genérico) o **503** (base no disponible). Un campo JSON desconocido o un parámetro faltante o mal tipado es siempre **400**.

| Método | Ruta | Éxito | Errores propios |
| --- | --- | --- | --- |
| GET | `/` | 200, front web estático | — |
| POST | `/editions` | 201 `EditionDto` | 400 dato faltante, en blanco o categoría repetida; 409 id repetido |
| GET | `/editions` · `/editions/{id}` | 200 lista / `EditionDto` | 404 |
| GET | `/editions/{id}/challenges` | 200 lista de `ChallengeDto` | 404 |
| POST | `/editions/{id}/challenges` | 201 `ChallengeDto` con v1 | 400 id o nombre inválido; 404 edición; 409 id repetido; 422 reglamento |
| GET | `/challenges/{id}` | 200 `ChallengeDto` | 404 |
| POST | `/challenges/{id}/rulebook/versions` | 201 `RulebookDto` | 404; 422 reglamento o ronda ya programada que la versión no cuenta |
| GET | `/challenges/{id}/rulebook/versions/{version}` | 200 `RulebookDto` | 400 número inválido; 404 |
| POST | `/editions/{id}/registrations` | 201 `RegistrationDto` | 400; 404; 409 duplicada o equipo existente; 422 inelegible |
| GET | `/editions/{id}/registrations` · `/editions/{id}/registrations/{teamId}` | 200 lista / `RegistrationDto` | 404 |
| PUT | `/editions/{id}/registrations/{teamId}` | 200 `RegistrationDto` | 400; 404; 422 inelegible |
| POST | `/challenges/{id}/rounds` | 201 `RoundDto` | 400 recursos o categoría ajena; 404; 409 scope repetido; 422 inelegible, fuera de fechas o ronda más allá de M |
| GET | `/challenges/{id}/rounds[?categoryId=]` · `/rounds/{id}` | 200 lista / `RoundDto` | 400 categoría ajena; 404 |
| POST | `/rounds/{id}/start` · `/rounds/{id}/completion` | 200 `RoundDto` | 404; 409 transición no permitida |
| POST | `/rounds/{id}/slots/{slotId}/start` · `/completion` | 200 `RoundDto` | 404; 409 transición no permitida |
| POST | `/rounds/{id}/slots/{slotId}/cancellation` | 200 `RoundDto` | 400 sin `actorId`/`reason`; 404; 409 turno empezado o con resultados |
| PUT | `/attempts/{id}/measurements` · `/attempts/{id}/judge-scores` | 200 `AttemptDto` | 400; 404 slot; 409 fuente repetida, otro desafío o turno cancelado; 422 mediciones o juez no asignado |
| GET | `/attempts/{id}/breakdown` | 200 `BreakdownDto` (`awaiting`, `items`, `bySource`, `revisions`) | 404 |
| POST | `/attempts/{id}/penalty-adjustments` | 200 `{attempt, standingsVersion}` | 400; 404; 409 estado |
| POST | `/attempts/{id}/disqualification` | 200 `{attempt, standingsVersion}` | 400; 404; 409 estado; 422 juez no asignado |
| POST | `/attempts/{id}/appeals` | 201 `AppealDto` | 400; 404; 409 estado del intento; 422 equipo ajeno |
| GET | `/attempts/{id}/appeals` · `/challenges/{id}/appeals?categoryId=` · `/appeals/{id}` | 200 lista / `AppealDto` | 400 sin `categoryId`; 404 |
| POST | `/appeals/{id}/review` | 200 `AppealDto` | 400; 404; 409 transición |
| POST | `/appeals/{id}/acceptance` | 200 `{appeal, standingsVersion}` | 400; 404; 409 transición o revisor; 422 correcciones |
| POST | `/appeals/{id}/rejection` | 200 `AppealDto` | 400; 404; 409 transición o revisor |
| GET | `/challenges/{id}/standings?categoryId=` | 200 `StandingsDto` | 400; 404 |
| GET | `/challenges/{id}/standings/versions/{v}?categoryId=` | 200 `StandingsVersionDto` | 400; 404 |
| POST | `/challenges/{id}/standings/versions?categoryId=` | 201 `StandingsVersionDto` (provisional) | 400; 404 |
| POST | `/challenges/{id}/standings/versions/{v}/publication?categoryId=` | 200 `StandingsDto` | 400; 404; 409 bloqueada, con cada motivo en `details` |

El detalle de cada cuerpo está en las secciones siguientes.

La edad mínima se evalúa en años cumplidos al inicio de la edición. `maxAge`
es opcional: omitido o `null` indica que la categoría no tiene edad máxima.
La demo requiere ser mayor de 12 años (desde 13), sin límite superior.

### Configurar el evento y los desafíos

`POST /editions` → 201:

```json
{"id": "ed-1", "name": "RoboLeague 2026", "editionNumber": 1,
 "tournament": {"id": "t-1", "name": "RoboLeague", "season": {"id": "s-2026", "year": 2026, "name": "Temporada 2026"}},
 "startDate": "2026-11-10", "endDate": "2026-11-12",
 "categories": [{"id": "cat-junior", "name": "Junior", "minMembers": 2, "maxMembers": 4,
                 "minAge": 12, "maxAge": 17, "maxWeightGrams": 2500}]}
```

`POST /editions/{id}/challenges` con `{"id", "name", "rulebook"}` → 201 con el desafío y su reglamento v1. `POST /challenges/{id}/rulebook/versions` con un `rulebook` → 201 con la versión nueva. Una versión cuya selección de rondas no puede contar una ronda ya programada del desafío (por ejemplo `best-n-of-m` con `outOf: 3` cuando existe la ronda 4) es **422**, con una línea por ronda (`"round 4 of category cat-junior is already scheduled and the new rulebook counts mejores 2 de 3 rondas"`). Un reglamento con problemas es **422** con cada problema en `details` (`"rule 'Faltas': missing parameter 'deductionPerPenalty'"`). Edición o desafío inexistente: 404; id repetido: 409. Una edición sin un dato obligatorio (`editionNumber`, id o nombre del torneo, id, nombre o año de la temporada, id o nombre de una categoría), con un id o nombre en blanco o con dos categorías del mismo id: 400. Un número omitido no se toma como 0: el error nombra el campo (`Missing field 'tournament.season.year'`).

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

Los puntajes se calculan en centésimas exactas (ver DESIGN 2.28). Los parámetros que son puntos (`basePoints`, `minPoints`, `pointsPerObjective`, `maxPoints`, `pointsPerRescued`, `bonus`, `deductionPerPenalty`, `deductionPerFault`, `deductionPerAbandoned` y el `maximum` del tope) admiten hasta dos decimales; con más, el reglamento es 422 (`"parameter 'bonus' is an amount of points and takes at most 2 decimals: 12.345"`). Tasas, pesos y tiempos (`pointsPerSecondUnder`, `weightMultiplier`, `penaltyPerExcessUnit`…) no tienen ese límite. En `time`, `minPoints` no puede superar a `basePoints`; en `milestone`, `threshold` es un número no negativo (si no, 422). Las respuestas siguen mostrando los puntos como números JSON (`147.5`).

Una métrica es `{"name", "source"}` con `source` = `AUTOMATIC_MEASUREMENTS` o `JUDGE_PANEL`. Toda métrica que lee una regla tiene que estar declarada en `metrics` con la misma fuente, una unidad (`COUNT`, `SECONDS`, `METERS`, `RATIO` o `POINTS`; `COUNT` solo acepta enteros) y un rango (`min` y, si tiene tope, `max`). Una métrica no declarada o de otra fuente, un rango invertido o una métrica declarada dos veces: 422. Una unidad desconocida: 400. Tiempo, objetivos, faltas, consumo y notas de jueces son campos fijos y no se declaran. Límite de bonificaciones: `capped` (`maximum`) o `unlimited`. Selección de rondas: `best-n-of-m` (`considered`, `outOf`) o `all-rounds`. Criterios: `higher-total`, `lower-time`, `lower-deductions` (menos puntos descontados por las deducciones), `higher-judge-score`. El primero es siempre `higher-total` (se ordena por puntaje) y ninguno se repite; los demás desempatan en el orden declarado.

### Inscribir y actualizar equipos

`GET /editions` devuelve una lista; `GET /editions/{editionId}` devuelve el detalle.
Ambos (y la respuesta de `POST /editions`) usan `{id, name, editionNumber,
tournament, startDate, endDate, categories, categoryDetails}`, con
`tournament` = `{id, name, description, season: {id, year, name}}`.
`categories` conserva los ids del contrato original; `categoryDetails` contiene
`{id, name, minMembers, maxMembers, minAge, maxAge, maxWeightGrams, maxLengthMm,
maxWidthMm, maxHeightMm}`. En `POST /editions`, las tres dimensiones máximas son
opcionales: cada una omitida/null conserva el default de **1000 mm**. Peso y
restricciones de integrantes/edad son obligatorios; peso/dimensiones deben ser
positivos y finitos. `GET /editions/{editionId}/challenges` devuelve desafíos de
esa edición con el mismo DTO de `GET /challenges/{id}`, ordenados por id.

`POST /editions/{editionId}/registrations` → **201**:

```json
{"categoryId": "cat-junior", "team": {
  "id": "team-a", "name": "Team A", "institution": "Universidad",
  "members": [{"id": "participant-a", "fullName": "Ana Pérez",
               "birthDate": "2010-01-01", "role": "LEADER"},
              {"id": "participant-b", "fullName": "Bruno Díaz",
               "birthDate": "2010-02-01", "role": "DEV"}],
  "robot": {"id": "robot-a", "name": "Bot A", "weightGrams": 1500,
            "lengthMm": 200, "widthMm": 200, "heightMm": 150,
            "actuatorCount": 2, "sensors": ["LIDAR"]},
  "documentation": {"documents": {"consent": "consent.pdf"}, "verifiedBy": "inspector-a"}
}}
```

Los datos completos del equipo son obligatorios, incluidas listas de miembros,
sensores y mapa de documentos. `institution` puede estar vacío; nombres, roles,
ids, sensores y referencias documentales no pueden estar vacíos. Un equipo sin
integrantes o sin verificación se evalúa por elegibilidad y devuelve **422** si
la categoría lo rechaza. `verifiedBy` omitido/null significa sin verificación;
si se informa, el servidor verifica el conjunto recibido con ese ActorId y su
Clock. Este flujo no incorpora autenticación ni upload. Peso en gramos,
dimensiones en milímetros, fechas de nacimiento ISO `YYYY-MM-DD`.

Para inscribir el estado canónico existente, enviar **exactamente una** alternativa:

```json
{"categoryId": "cat-junior", "teamId": "team-a"}
```

Enviar `team` y `teamId` juntos, ninguno, o un dato requerido faltante → **400**.
Un objeto `team` cuyo id ya existe no sobrescribe el equipo → **409**, al igual
que una inscripción duplicada. Referencias inexistentes → **404**. Elegibilidad
rechazada → **422** con motivos en `ErrorDto.details`. Un integrante o un robot
compite para un solo equipo por edición: un equipo que comparte alguno (por id)
con otro equipo inscripto en la edición es **422**, con un motivo por cada uno
(`"Participant p-1 already competes for team team-a"`). Lo mismo vale para el PUT
de una inscripción, en cada edición del equipo.

La respuesta de inscripción es `{editionId, teamId, categoryId, registeredAt,
referenceDate, team}`. `team` contiene id/nombre/institución, miembros y robot
con los mismos campos del request; documentación se representa con
`{documents, verified, verifiedAt, verifiedBy, revocationReason}`. registeredAt
lo fija el servidor; referenceDate es la fecha de inicio de la edición. Los
requests no aceptan registeredAt/referenceDate/verifiedAt ni otros metadatos de
respuesta: propiedades desconocidas devuelven **400**.

`GET /editions/{editionId}/registrations` devuelve esas vistas, ordenadas por
registeredAt y TeamId; `GET /editions/{editionId}/registrations/{teamId}` devuelve
una. Siempre muestran el equipo canónico actual, también si fue actualizado
desde otra edición. No se guardan equipos duplicados dentro de Edition.

`PUT /editions/{editionId}/registrations/{teamId}` → **200**, con body
`{categoryId, team}` y **candidato completo**, incluso si solo cambia la categoría.
El id de team debe coincidir con el path y la inscripción debe existir. Se valida
el equipo contra la nueva categoría en esa edición y contra las categorías
actuales de todas sus otras inscripciones **antes de guardar ambos cambios**.
Una validación rechazada conserva el equipo y todas las relaciones anteriores.
registeredAt/referenceDate permanecen iguales; la verificación del conjunto
recibido vuelve a usar la hora del servidor.

### Programar y consultar rondas por HTTP

`POST /challenges/{challengeId}/rounds` → **201**:

```json
{"categoryId": "cat-junior", "roundNumber": 1, "roundName": "Clasificatoria",
 "startTime": "2026-11-10T10:00:00", "slotDurationSeconds": 300, "intervalSeconds": 60,
 "tracks": [{"id": "track-1", "name": "Pista 1", "surfaceType": "Madera", "isActive": true}],
 "judges": [{"id": "judge-1", "fullName": "Juez Uno", "specialty": "General"}]}
```

Todos los campos son obligatorios. La edición se deriva del desafío; enviar
editionId en el body devuelve **400**. Tiempo local ISO, sin offset, en la zona
configurada del servidor. Duración en segundos positivos; pausa de pista en
segundos no negativos. Número de ronda positivo. Los recursos se suministran
por request, sin crear catálogos persistidos. Pistas inactivas se excluyen;
recursos duplicados, ausencia de pista activa/juez y categoría ajena → **400**;
scope duplicado → **409**; equipos canónicos inelegibles → **422**. Todos los
turnos tienen que caer dentro de las fechas de la edición (un turno que termina
a medianoche pertenece al día anterior); si no, **422** y no se guarda nada.

Respuesta: `{roundId, challengeId, editionId, categoryId, roundNumber, name,
status, slots}`. Cada slot expone `{slotId, teamId, track, judges, startTime,
endTime, intervalSeconds, status, cancellation}`; track/judges contienen los mismos campos
públicos que en el request y `cancellation` es `null` salvo en un turno cancelado. Slots se ordenan por inicio, TrackId y SlotId, y no
se expone el agregado directamente. El scheduler asigna un juez por slot. Ese
juez puede usar el slotId para ambos endpoints de captura existentes.

`GET /challenges/{challengeId}/rounds` admite filtro opcional `?categoryId=...`.
Devuelve una lista ordenada por número, categoría e id de ronda. Un filtro de
categoría no ofrecida devuelve **400**; una categoría válida sin rondas devuelve
**200** con `[]`. `GET /rounds/{roundId}` devuelve el mismo DTO del alta.
Las listas de inscripciones/desafíos/rondas vacías son **200**; sus padres deben
existir. Edición, inscripción, desafío o ronda inexistentes devuelven **404**.
Las consultas no escriben ni cambian estados.

Ciclo de vida, sin body salvo la cancelación; todas responden **200** con el
mismo `RoundDto`:

- `POST /rounds/{roundId}/start`: la ronda pasa de `SCHEDULED` a `IN_PROGRESS`.
- `POST /rounds/{roundId}/slots/{slotId}/start` y `.../completion`: el turno pasa
  a `IN_PROGRESS` y luego a `COMPLETED`; exigen la ronda en curso.
- `POST /rounds/{roundId}/completion`: exige todos los turnos completados o
  cancelados.
- `POST /rounds/{roundId}/slots/{slotId}/cancellation` con
  `{"actorId": "org-1", "reason": "El equipo no se presentó"}`: cancela un turno
  que no empezó y guarda `cancellation {actorId, reason, cancelledAt}` (hora del
  servidor). Libera pista y juez, y la tabla deja de esperar ese turno para
  publicarse. Un turno que ya tiene resultados no se cancela (**409**): se
  descalifica su intento.

Una transición no permitida (empezar dos veces, completar con turnos
pendientes, cancelar un turno empezado o una ronda completada) es **409**; ronda
o slot inexistentes, **404**; sin `actorId` o `reason`, **400**. El ciclo de vida
no depende de los resultados: capturar un resultado no empieza ni completa el
turno, pero un turno cancelado no acepta resultados (**409**).

### Cargar los resultados de un intento

El id de un intento es su turno: el slot y el número de intento (`<slotId>-<n>`, por ejemplo `slot-7-1`). El turno tiene que estar en una ronda programada, el juez que carga tiene que estar asignado al slot y el equipo es el del slot. El `challengeId` enviado debe coincidir con el desafío de la ronda, incluso en la primera captura; un desafío diferente devuelve 409 sin crear ni modificar el intento. El primer resultado abre el intento con la versión vigente del reglamento del desafío; los siguientes se puntúan con esa misma versión aunque se publique otra.

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
| La misma fuente dos veces, un intento ya puntuado (las correcciones van por ajuste o apelación), un intento de otro desafío, un turno cancelado | 409 |
| Un id sin número, un campo obligatorio que falta, un valor negativo o no finito, un panel de jueces sin ninguna nota (`scores: {}`) | 400 |
| Un slot que no está en ninguna ronda, un intento que no existe (desglose) | 404 |

`GET /attempts/{id}/breakdown` → 200 con lo que el intento todavía espera (`awaiting`), su puntaje, cada ítem de la última revisión (tope y piso incluidos), lo que aportó cada fuente (`bySource`) y cada revisión con su versión de reglamento, autor y motivo.

Un intento puntuado se corrige fuera de una apelación de dos formas. Las dos recalculan la tabla de su desafío y categoría y responden **200** con `{attempt, standingsVersion}` (`attempt` es el mismo DTO de la captura):

- `POST /attempts/{id}/penalty-adjustments` con `{"actorId": "arb-1", "reason": "Toque de borde visto en video", "additionalPenalties": 2}`: suma esas faltas a la última medición y agrega una revisión puntuada con el reglamento del intento (estado `ADJUSTED`). `additionalPenalties` tiene que ser positivo (**400**).
- `POST /attempts/{id}/disqualification` con `{"judgeId": "j-1", "reason": "Robot fuera de reglamento"}`: el juez tiene que estar asignado al turno (**422** si no). El intento queda `DISQUALIFIED`, conserva sus revisiones y deja de contar para la tabla (`score: null`).

Un campo faltante es **400**; un intento inexistente, **404**; un intento que espera una fuente, está en apelación o ya fue descalificado, **409**.

### Apelaciones

`POST /attempts/{id}/appeals` → **201**:

```json
{"teamId": "t-b", "reason": "Penalizacion inexistente", "evidence": "Video pista"}
```

El equipo tiene que ser el del intento (si no, **422**); un intento inexistente es **404**. Responde el `AppealDto` (`id`, `attemptId`, `teamId`, `reason`, `evidence`, `status`, `reviewerId`, `submittedAt`, `resolutionNotes`, `resolvedAt`, `revisedMetrics`). `GET /attempts/{id}/appeals` y `GET /challenges/{id}/appeals?categoryId=` listan las de ese intento o de la categoría, más antiguas primero. `GET /appeals/{id}` devuelve una.

`POST /appeals/{id}/review` con `{"reviewerId"}` → 200 `UNDER_REVIEW`. Volver a revisarla es **409**; sin `reviewerId`, **400**; una apelación inexistente, **404**.

`POST /appeals/{id}/acceptance` → 200:

```json
{"reviewerId": "j-arb", "notes": "Penalizaciones corregidas",
 "measurements": {"timeSeconds": 45, "objectives": 5, "penalties": 0, "consumption": 90}}
```

`measurements` y `judgeScores` son opcionales, con la misma forma que al cargar un resultado. Hace falta al menos una fuente. El intento se puntúa con **su** reglamento; si las correcciones no caben, **422** y no cambia nada. La respuesta es `{appeal, standingsVersion}`: aceptar recalcula la tabla de su desafío y categoría.

`POST /appeals/{id}/rejection` con `{"reviewerId", "notes"}` → 200. Conserva el puntaje. Solo el revisor que tomó el reclamo puede aceptarlo o rechazarlo (**409**).

### Tabla de posiciones

Hay una tabla por desafío y categoría. Cada recálculo agrega una versión; ninguna se edita. El orden sale del `ranking` del reglamento vigente (mejores N de M y la cadena de desempate).

`GET /challenges/{id}/standings?categoryId=` → 200 con `{challengeId, categoryId, versions, latest, official, pending}`. Antes del primer cálculo `latest` y `official` son `null`. `pending` dice `{openAppeals, unfinishedTurns, outdated}`. `GET .../standings/versions/{v}` devuelve esa versión con sus filas; sin tabla calculada o sin esa versión, **404** (también al publicarla). Sin `categoryId`, **400**.

`POST /challenges/{id}/standings/versions?categoryId=` → **201** con la versión nueva (provisional).

`POST /challenges/{id}/standings/versions/{v}/publication?categoryId=` → 200:

```json
{"publishedBy": "org-1", "notes": "Publicacion definitiva post-arbitraje"}
```

Solo se publica la última versión, y solo si no hay apelaciones abiertas, no quedan turnos sin resultado y los resultados no cambiaron desde ese cálculo. Si algo lo bloquea, **409** con cada motivo en `details`. Publicar reemplaza a la oficial anterior, que queda `REPLACED`.

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

Programación revalida participantes; programación y standings resuelven el
estado actual desde TeamRepository. Rechazos de validación no dejan cambios
parciales. Team y Edition (con sus Registration) se guardan en Postgres
(migraciones `V6`/`V7`) y sobreviven reinicios. Si dos pedidos cambian la misma
edición o el mismo equipo a la vez (por ejemplo, dos inscripciones), el segundo
recibe 409 y debe recargar y reintentar.

### Rondas por desafío y programación paralela

`ScheduleRoundCommand` incluye ChallengeId y EditionId; el desafío debe pertenecer
a esa edición y la categoría estar ofrecida. Se revalidan los equipos canónicos
y se ordenan las inscripciones por registeredAt, con TeamId como desempate.
El scope desafío/categoría/número es único. RoundRepository permite buscar por
RoundId, scope, desafío y SlotId, además de consultar todas las rondas.

El scheduler asigna un juez por turno y elige la combinación pista/juez con
inicio libre más temprano, desempata por el orden de los recursos del command.
No usa pistas inactivas y rechaza ids de recursos duplicados. Con dos pistas,
dos jueces, duración de 5 minutos y pausa de pista de 1 minuto: A/B compiten
10:00–10:05 y C/D 10:06–10:11. Con un juez, el segundo equipo usa la otra pista
10:05–10:10. Se respetan ocupaciones guardadas por pista, juez y equipo; las
ventanas son [inicio, fin), y la pausa pertenece solo a la pista.

Round y Slot son inmutables. Las operaciones de Round devuelven el nuevo
agregado y protegen sus slots: SCHEDULED → IN_PROGRESS → COMPLETED, con
SCHEDULED → CANCELLED para turnos. Completar ronda requiere todos sus slots
completados o cancelados. Cancelar libera recursos. Recibir fuentes de medición
no cambia estos estados; el juez asignado puede cargar ambas fuentes de un
intento mixto. Las notas individuales del panel siguen siendo datos de scoring.

Las rondas se guardan en Postgres (migración `V8`) con sus slots completos;
Postgres asegura el scope único y una escritura obsoleta de la misma ronda
recibe 409. Dos programaciones concurrentes de rondas distintas todavía no se
excluyen por pista o juez.

## Front web: uso y estado de integración

1. Elegí edición, desafío y categoría en la barra superior. Los nombres vienen
   del servidor; la web conserva los ids como referencias.
2. **Equipos** lista inscripciones, permite editar candidatos completos, agregar
   integrantes/documentos y elegir equipos de otras ediciones. Muestra límites
   de categoría. Un 422 conserva el formulario y todos los motivos del servidor;
   solo un cambio confirmado actualiza la lista.
3. **Rondas y turnos** presenta columnas por pista con horarios, equipo y jueces.
   Permite programar con duración/pausa en segundos y recursos existentes o
   nuevos. Los recursos sugeridos conservan sus ids entre desafíos/categorías;
   no se calcula el horario en JavaScript. Seleccionar un turno abre resultados.
4. **Cargar resultados** deriva `<slotId>-<numero>` con número positivo y permite
   elegir el juez asignado y una fuente pendiente. Los campos de métricas se
   generan a partir del reglamento, incluyendo fuente/unidad/rango. Las notas
   del panel se cargan por juez con nombres de recursos conocidos; no es un
   editor de JSON. Ambas fuentes se envían a los endpoints existentes.
5. **Desglose e historial** consulta puntaje, fuentes pendientes/recibidas, ítems,
   fórmulas, subtotales, notas, aportes y revisiones disponibles. Un puntaje
   pendiente/descalificado no se representa como cero. Los desgloses y totales
   son los del servidor, sin recalcular scoring en el navegador.
6. **Apelaciones** lista los reclamos de la categoría, permite presentar uno
   sobre un turno/intento y avanzar revisión, aceptación o rechazo. La
   corrección de una aceptación la valida el servidor.
7. **Tabla de posiciones** muestra versiones, filas, explicación de cada puesto
   y lo que bloquearía publicar ahora. Recalcular y publicar son pedidos al
   servidor; un 409 lista cada motivo.

Si el intento fue abierto con un reglamento anterior, la web consulta
`GET /challenges/{id}/rulebook/versions/{version}` (200 con RulebookDto, 404 si
no existe desafío/versión, 400 si el número es inválido) para presentar las métricas
históricas. Esa consulta no publica una versión ni modifica las existentes.
Una primera captura usa el reglamento vigente consultado al servidor.

Los envíos bloquean sus formularios y selección de contexto para evitar doble
captura accidental. Un **409** bloquea nuevas mutaciones hasta usar **Recargar
datos**; nunca se reintenta una mutación automáticamente. Los nombres, motivos y
mensajes se renderizan como texto, sin HTML suministrado por usuarios. No hay
login ni seguridad por roles simulada; la API conserva sus responsabilidades.

**Apelaciones y tabla (frente 4):** las pantallas consumen la API real. Presentar
un reclamo, tomarlo en revisión, aceptarlo con mediciones corregidas o
rechazarlo, recalcular y publicar van al servidor. El navegador no ordena
filas ni decide si se puede publicar: un 409 muestra cada motivo (versión
vieja, apelaciones abiertas, turnos sin resultado o resultados posteriores al
cálculo). Equipos, ediciones, rondas y standings se guardan en Postgres.

### Verificación del frontend

Desde la raíz:

```bash
mvn -B verify
node --test roboleague-api/src/test/frontend/*.test.mjs
```

Node 20+ se usa **solo para tests**, sin instalar paquetes ni compilar assets.
La CI ejecuta también esos tests. Maven prueba entrega de HTML/CSS/módulos por
Spring, consulta de reglamentos históricos y regresiones de la demo/API. Los
tests de JS cubren contratos, auditoría de solo lectura, métricas por fuente,
recursos por identidad, horarios paralelos y errores sin retry. Son pruebas de
módulos, **no una corrida de navegador**.

En esta implementación no se pudo ejecutar la verificación visual/interactiva:
el navegador de la sesión no estuvo disponible. Sigue pendiente comprobar
escritorio, teclado y flujos reales de inscripción válida/inelegible,
edición rechazada sin pérdida, dos pistas paralelas, captura mixta, desglose
con tope, apelación aceptada y publicación de la última versión desde la web.
Las pruebas HTTP cubren los contratos del backend, sin acreditar por sí solas
esa comprobación de UI.

## Cómo sumar lo tuyo

**Un endpoint.** Controller en `roboleague-api/src/main/java/com/roboleague/api/<contexto>/`, con su DTO. Si el caso de uso es nuevo, su `@Bean` va en `UseCaseConfig`. El test extiende `ApiTest` y usa ids propios (el contexto y la base se comparten entre clases de test).

**Un tipo de regla nuevo.** Clase en `roboleague-domain/.../evaluation/rules/` que implementa la interfaz de su sección (`BaseRule`, `BonusRule` o `DeductionRule`), con `TYPE`, constantes para los nombres de sus parámetros, `static from(RuleDefinition)` y `definition()` (los dos usan las mismas constantes), y `metrics()` con las métricas con nombre que lee. Un registro en la sección que le corresponde en `RuleCatalog.standard()`, un caso en `RuleCatalogTest.everyRuleType()` y en `ScoreRuleSectionsTest`, y una fila en la tabla de "Configurar el evento y los desafíos". Las reglas existentes no se tocan.

**Validar una captura contra el reglamento.** `rulebook.check(fuente, mediciones)` revisa lo que mandó una fuente: que estén todas las métricas declaradas para ella, ninguna de más, cada una en su unidad y rango. Devuelve `MeasurementCheck.Accepted` o `Rejected(problemas)`. `Attempt.receive` lo llama con cada fuente que llega y no cambia nada si la rechaza; `ReceiveResultUseCase` lo traduce a 422.

**Leer el puntaje de un intento.** `attempt.countableScore()` es el desglose que cuenta para la tabla: vacío mientras espera una fuente o si está descalificado, nunca un cero de relleno. Cada revisión (`getRevisionHistory()`) guarda la versión de reglamento que la puntuó.

**Persistencia de un agregado.** Seguir el caso de `Appeal` en `roboleague-infrastructure/.../repository/jpa/`:

1. Migración nueva en `src/main/resources/db/migration/V<n>__<descripcion>.sql`. Nunca editar una migración ya mergeada.
2. `XxxJpaEntity` (package-private, solo campos) y `SpringDataXxx extends JpaRepository`.
3. `XxxMapper` en los dos sentidos. Para volver al dominio, el agregado expone `Xxx.restore(...)`: así no necesita constructor vacío ni setters.
4. `JpaXxxRepository implements XxxRepository`, anotado `@Repository`. Para rechazar escrituras concurrentes con `@Version`, seguir `JpaAttemptRepository` si el agregado es mutable o `JpaEditionRepository` si es inmutable.
5. La API lo descubre por escaneo; no registrar otro bean para el mismo puerto (si hay dos, la app no arranca). El adaptador en memoria, si existe, queda para tests de casos de uso.
6. `JpaXxxRepositoryTest` con `@DataJpaTest` e `@Import({PostgresContainer.class, JpaXxxRepository.class})`.

## Más

- [`DESIGN.md`](DESIGN.md): contextos, decisiones de diseño, patrones y alternativas descartadas; el capítulo 6 resume clases, refactorizaciones y deuda de F1, F2 y F3, y el capítulo 7 los patrones que se decidió no aplicar.
