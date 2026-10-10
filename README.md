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

- Una edición (`ed-1`, categoría `cat-junior`, desde 13 años cumplidos al inicio de la edición, sin edad máxima) con tres desafíos: Laberinto (`ch-maze`), Seguidor de línea (`ch-line`, con su reglamento en v2) y Rescate (`ch-rescue`, mixto: exige mediciones automáticas y panel de jueces). Entre los tres usan los doce tipos de regla (base, bonificaciones y deducciones), la compuesta "Desempeño en pista", penalizaciones, bonificaciones con tope (40 / 30 / 30), mejores N de M y criterios de desempate encadenados. Se consultan con `GET /challenges/{id}`.
- Sobre Laberinto: dos equipos, una ronda, dos intentos (el desglose muestra el recorte del tope), un ranking provisional, una apelación aceptada con recálculo y la publicación oficial.
- Sobre Rescate (F3): una ronda con un intento de CyberTeam que recibió las mediciones y el panel de jueces (147,5) y uno de TitanTeam que tiene las mediciones y espera el panel (`GET /attempts/{id}/breakdown` lo muestra pendiente).

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
- Cada caso de uso corre en una transacción (`TransactionalUseCases`) que cubre Postgres. Los repositorios en memoria no ofrecen rollback ante fallos técnicos entre escrituras; los rechazos de validación se comprueban antes de guardar.
- Referencia: `AppealController` y `AppealControllerTest`.

### Contratos

| Método | Ruta | Estado |
| --- | --- | --- |
| POST | `/appeals/{id}/review` | hecho |
| POST | `/editions` | hecho |
| POST | `/editions/{id}/challenges` | hecho |
| POST | `/challenges/{id}/rulebook/versions` | hecho |
| GET | `/challenges/{id}` | hecho |
| GET | `/challenges/{id}/rulebook/versions/{version}` | hecho |
| GET | `/editions` · `/editions/{id}` | hecho |
| GET | `/editions/{id}/challenges` | hecho |
| POST · GET | `/editions/{id}/registrations` | hecho |
| GET · PUT | `/editions/{id}/registrations/{teamId}` | hecho |
| POST · GET | `/challenges/{id}/rounds` | hecho |
| GET | `/rounds/{id}` | hecho |
| PUT | `/attempts/{id}/measurements` | hecho |
| PUT | `/attempts/{id}/judge-scores` | hecho |
| GET | `/attempts/{id}/breakdown` | hecho |
| POST · GET | `/attempts/{id}/appeals` | hecho |
| GET | `/challenges/{id}/appeals` · `/appeals/{id}` | hecho |
| POST | `/appeals/{id}/acceptance` · `/rejection` | hecho |
| GET | `/challenges/{id}/standings[/versions/{v}]` | hecho |
| POST | `/challenges/{id}/standings/versions` | hecho |
| POST | `/challenges/{id}/standings/versions/{v}/publication` | hecho |

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

### Inscribir y actualizar equipos

`GET /editions` devuelve una lista; `GET /editions/{editionId}` devuelve el detalle.
Ambos usan `{id, name, startDate, endDate, categories, categoryDetails}`.
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
que una inscripción duplicada. Referencias inexistentes → **400**. Elegibilidad
rechazada → **422** con motivos en `ErrorDto.details`.

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
scope duplicado → **409**; equipos canónicos inelegibles → **422**.

Respuesta: `{roundId, challengeId, editionId, categoryId, roundNumber, name,
status, slots}`. Cada slot expone `{slotId, teamId, track, judges, startTime,
endTime, intervalSeconds, status}`; track/judges contienen los mismos campos
públicos que en el request. Slots se ordenan por inicio, TrackId y SlotId, y no
se expone el agregado directamente. El scheduler asigna un juez por slot. Ese
juez puede usar el slotId para ambos endpoints de captura existentes.

`GET /challenges/{challengeId}/rounds` admite filtro opcional `?categoryId=...`.
Devuelve una lista ordenada por número, categoría e id de ronda. Un filtro de
categoría no ofrecida devuelve **400**; una categoría válida sin rondas devuelve
**200** con `[]`. `GET /rounds/{roundId}` devuelve el mismo DTO del alta.
Las listas de inscripciones/desafíos/rondas vacías son **200**; sus padres deben
existir. Edición, inscripción, desafío o ronda inexistentes devuelven **400**.
Las consultas no escriben ni cambian estados.

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
| La misma fuente dos veces, un intento ya puntuado (las correcciones van por ajuste o apelación), un intento de otro desafío | 409 |
| Un slot que no está en ninguna ronda, un id sin número, un campo obligatorio que falta, un valor negativo | 400 |

`GET /attempts/{id}/breakdown` → 200 con lo que el intento todavía espera (`awaiting`), su puntaje, cada ítem de la última revisión (tope y piso incluidos), lo que aportó cada fuente (`bySource`) y cada revisión con su versión de reglamento, autor y motivo.

### Apelaciones

`POST /attempts/{id}/appeals` → **201**:

```json
{"teamId": "t-b", "reason": "Penalizacion inexistente", "evidence": "Video pista"}
```

El equipo tiene que ser el del intento. Responde el `AppealDto` (`id`, `attemptId`, `teamId`, `reason`, `evidence`, `status`, `reviewerId`, `submittedAt`, `resolutionNotes`, `resolvedAt`, `revisedMetrics`). `GET /attempts/{id}/appeals` y `GET /challenges/{id}/appeals?categoryId=` listan las de ese intento o de la categoría, más antiguas primero. `GET /appeals/{id}` devuelve una.

`POST /appeals/{id}/review` con `{"reviewerId"}` → 200 `UNDER_REVIEW`. Volver a revisarla es **409**.

`POST /appeals/{id}/acceptance` → 200:

```json
{"reviewerId": "j-arb", "notes": "Penalizaciones corregidas",
 "measurements": {"timeSeconds": 45, "objectives": 5, "penalties": 0, "consumption": 90}}
```

`measurements` y `judgeScores` son opcionales, con la misma forma que al cargar un resultado. Hace falta al menos una fuente. El intento se puntúa con **su** reglamento; si las correcciones no caben, **422** y no cambia nada. La respuesta es `{appeal, standingsVersion}`: aceptar recalcula la tabla de su desafío y categoría.

`POST /appeals/{id}/rejection` con `{"reviewerId", "notes"}` → 200. Conserva el puntaje. Solo el revisor que tomó el reclamo puede aceptarlo o rechazarlo (**409**).

### Tabla de posiciones

Hay una tabla por desafío y categoría. Cada recálculo agrega una versión; ninguna se edita. El orden sale del `ranking` del reglamento vigente (mejores N de M y la cadena de desempate).

`GET /challenges/{id}/standings?categoryId=` → 200 con `{challengeId, categoryId, versions, latest, official, pending}`. Antes del primer cálculo `latest` y `official` son `null`. `pending` dice `{openAppeals, unfinishedTurns, outdated}`. `GET .../standings/versions/{v}` devuelve esa versión con sus filas.

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
parciales en memoria. Team/Edition/Registration todavía no tienen persistencia
Postgres: sus datos se pierden al reiniciar y no hay rollback en memoria ante
fallos técnicos entre escrituras. Su JPA corresponde al frente 5; los endpoints
HTTP de estos flujos están implementados en la spec 05.

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

La spec 04 implementa dominio, casos de uso, adaptador memory y rehidratación.
Rondas en memoria se pierden al reiniciar y no garantizan exclusión entre
programaciones concurrentes. Su JPA/locking corresponde al frente 5; los
endpoints de programación y consulta están implementados en la spec 05.

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
`GET /challenges/{id}/rulebook/versions/{version}` (200 con RulebookDto, 400 si
no existe desafío/versión o si el número es inválido) para presentar las métricas
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
cálculo). Equipos/ediciones/rondas siguen en memoria; la UI no los presenta
como durables. Las standings sí se guardan en Postgres.

### Verificación del frontend

Desde la raíz:

```bash
mvn -B verify
node --experimental-default-type=module --test roboleague-api/src/test/frontend/*.test.mjs
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
4. `JpaXxxRepository implements XxxRepository`, anotado `@Repository`.
5. Borrar el bean en memoria de `InMemoryRepositoryConfig` (si quedan los dos, la app no arranca).
6. `JpaXxxRepositoryTest` con `@DataJpaTest` e `@Import({PostgresContainer.class, JpaXxxRepository.class})`.

## Más

- [`DESIGN.md`](DESIGN.md): contextos, decisiones de diseño, patrones y alternativas descartadas.
