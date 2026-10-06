# RoboLeague

Plataforma para competencias de robótica: torneos, elegibilidad de equipos, programación de rondas, evaluación de intentos, puntaje explicable, rankings con desempate y apelaciones.

## Requisitos

- Java 25
- Maven 3.6.3 o superior
- Docker (Postgres con `docker compose`; los tests de repositorio y de API lo usan vía Testcontainers)

## Correr la demo

Desde la raíz del repo:

```bash
docker compose up -d --wait
mvn -B package -DskipTests
java -jar roboleague-api/target/roboleague-api-1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

El perfil `demo` vacía la base, aplica las migraciones y carga `DemoFixture` a través de los casos de uso. Cada corrida termina en el mismo estado:

- Una edición (`ed-1`, categoría `cat-junior`) con tres desafíos: Laberinto (`ch-maze`), Seguidor de línea (`ch-line`, con su reglamento en v2) y Rescate (`ch-rescue`, mixto: exige mediciones automáticas y panel de jueces). Entre los tres usan los diez tipos de regla, la compuesta "Desempeño en pista", penalizaciones, bonificaciones con tope (40 / 30 / 30), mejores N de M y criterios de desempate encadenados. Se consultan con `GET /challenges/{id}`.
- Sobre Laberinto: dos equipos, una ronda, dos intentos (el desglose muestra el recorte del tope), un ranking provisional, una apelación aceptada con recálculo y la publicación oficial.

Capturar en el desafío mixto, programar rondas por desafío y ver la tabla con mejores N de M son de los otros frentes; hoy la demo los deja configurados.

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

  Un caso de uso que devuelva un resultado `sealed` se traduce en su controller con un `switch` (por ejemplo `Published → 200`, `Rejected → 409`).
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
| PUT | `/attempts/{id}/measurements` | pendiente |
| PUT | `/attempts/{id}/judge-scores` | pendiente |
| GET | `/attempts/{id}/breakdown` | pendiente |
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
{"scoring": {
   "rules": [
     {"type": "composite", "name": "Desempeño en pista", "rules": [
       {"type": "time", "name": "Tiempo", "numbers": {"basePoints": 100, "targetTimeSeconds": 60,
         "pointsPerSecondUnder": 1.5, "deductionPerSecondOver": 2, "minPoints": 0}},
       {"type": "objectives", "name": "Objetivos", "numbers": {"pointsPerObjective": 20, "totalObjectives": 5,
         "allCompletedBonus": 25}}]},
     {"type": "penalty", "name": "Faltas de pista", "numbers": {"deductionPerPenalty": 15}}],
   "bonuses": [
     {"type": "milestone", "name": "Checkpoint", "numbers": {"threshold": 1, "bonus": 30},
      "metrics": {"metric": {"name": "checkpoint", "source": "AUTOMATIC_MEASUREMENTS"}}}],
   "bonusLimit": {"type": "capped", "numbers": {"maximum": 40}}},
 "ranking": {"roundSelection": {"type": "best-n-of-m", "numbers": {"considered": 3, "outOf": 5}},
             "criteria": ["higher-total", "lower-time", "fewer-penalties"]}}
```

| `type` | `numbers` | `metrics` |
| --- | --- | --- |
| `time` | `basePoints`, `targetTimeSeconds`, `pointsPerSecondUnder`, `deductionPerSecondOver`, `minPoints` | — |
| `objectives` | `pointsPerObjective`, `totalObjectives`, `allCompletedBonus` | — |
| `penalty` | `deductionPerPenalty` | — |
| `judges` | `weightMultiplier` | — |
| `resource-consumption` | `maxAllowedConsumption`, `penaltyPerExcessUnit` | — |
| `counted-fault` | `freeAllowance`, `deductionPerFault` | `faults` |
| `precision` | `maxPoints` | `accuracy` |
| `victims` | `totalVictims`, `pointsPerRescued`, `deductionPerAbandoned` | `rescued` |
| `milestone` | `threshold`, `bonus` | `metric` |
| `composite` | — | — (lleva `rules`) |

Una métrica es `{"name", "source"}` con `source` = `AUTOMATIC_MEASUREMENTS` o `JUDGE_PANEL`. Límite de bonificaciones: `capped` (`maximum`) o `unlimited`. Selección de rondas: `best-n-of-m` (`considered`, `outOf`) o `all-rounds`. Criterios: `higher-total`, `lower-time`, `fewer-penalties`, `higher-judge-score`. El primero es siempre `higher-total` (se ordena por puntaje) y ninguno se repite; los demás desempatan en el orden declarado.

## Cómo sumar lo tuyo

**Un endpoint.** Controller en `roboleague-api/src/main/java/com/roboleague/api/<contexto>/`, con su DTO. Si el caso de uso es nuevo, su `@Bean` va en `UseCaseConfig`. El test extiende `ApiTest` y usa ids propios (el contexto y la base se comparten entre clases de test).

**Un tipo de regla nuevo.** Clase en `roboleague-domain/.../evaluation/rules/` que implementa `ScoreRule`, con `TYPE`, constantes para los nombres de sus parámetros, `static from(RuleDefinition)` y `definition()` (los dos usan las mismas constantes). Una entrada en `RuleCatalog.standard()`, un caso en `RuleCatalogTest.everyRuleType()` y una fila en la tabla de "Configurar el evento y los desafíos". Las reglas existentes no se tocan.

**Persistencia de un agregado.** Seguir el caso de `Appeal` en `roboleague-infrastructure/.../repository/jpa/`:

1. Migración nueva en `src/main/resources/db/migration/V<n>__<descripcion>.sql`. Nunca editar una migración ya mergeada.
2. `XxxJpaEntity` (package-private, solo campos) y `SpringDataXxx extends JpaRepository`.
3. `XxxMapper` en los dos sentidos. Para volver al dominio, el agregado expone `Xxx.restore(...)`: así no necesita constructor vacío ni setters.
4. `JpaXxxRepository implements XxxRepository`, anotado `@Repository`.
5. Borrar el bean en memoria de `InMemoryRepositoryConfig` (si quedan los dos, la app no arranca).
6. `JpaXxxRepositoryTest` con `@DataJpaTest` e `@Import({PostgresContainer.class, JpaXxxRepository.class})`.

## Más

- [`DESIGN.md`](DESIGN.md): contextos, decisiones de diseño, patrones y alternativas descartadas.
