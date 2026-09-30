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

El perfil `demo` vacía la base, aplica las migraciones y carga `DemoFixture` a través de los casos de uso: dos equipos, una ronda, dos intentos, un ranking provisional, una apelación aceptada con recálculo y la publicación oficial. Cada corrida termina en el mismo estado.

Sin el perfil `demo`, la app levanta en `http://localhost:8080` sobre la base tal como esté.

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
| POST | `/editions` | pendiente |
| POST | `/editions/{id}/challenges` | pendiente |
| POST | `/editions/{id}/registrations` | pendiente |
| POST | `/challenges/{id}/rounds` | pendiente |
| PUT | `/attempts/{id}/measurements` | pendiente |
| PUT | `/attempts/{id}/judge-scores` | pendiente |
| GET | `/attempts/{id}/breakdown` | pendiente |
| POST | `/attempts/{id}/appeals` | pendiente |
| POST | `/appeals/{id}/acceptance` · `/rejection` | pendiente |
| GET | `/challenges/{id}/standings[/versions/{v}]` | pendiente |
| POST | `/challenges/{id}/standings/versions/{v}/publication` | pendiente |

## Cómo sumar lo tuyo

**Un endpoint.** Controller en `roboleague-api/src/main/java/com/roboleague/api/<contexto>/`, con su DTO. Si el caso de uso es nuevo, su `@Bean` va en `UseCaseConfig`. El test extiende `ApiTest` y usa ids propios (el contexto y la base se comparten entre clases de test).

**Persistencia de un agregado.** Seguir el caso de `Appeal` en `roboleague-infrastructure/.../repository/jpa/`:

1. Migración nueva en `src/main/resources/db/migration/V<n>__<descripcion>.sql`. Nunca editar una migración ya mergeada.
2. `XxxJpaEntity` (package-private, solo campos) y `SpringDataXxx extends JpaRepository`.
3. `XxxMapper` en los dos sentidos. Para volver al dominio, el agregado expone `Xxx.restore(...)`: así no necesita constructor vacío ni setters.
4. `JpaXxxRepository implements XxxRepository`, anotado `@Repository`.
5. Borrar el bean en memoria de `InMemoryRepositoryConfig` (si quedan los dos, la app no arranca).
6. `JpaXxxRepositoryTest` con `@DataJpaTest` e `@Import({PostgresContainer.class, JpaXxxRepository.class})`.

## Más

- [`DESIGN.md`](DESIGN.md): contextos, decisiones de diseño, patrones y alternativas descartadas.
