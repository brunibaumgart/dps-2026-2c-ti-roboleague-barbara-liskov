# API y composition root

Leer también `../AGENTS.md`. Aplicación en `com.roboleague.RoboLeagueApplication`;
controllers, DTOs, config y demo bajo `src/main/java/com/roboleague/api`.

## Contratos REST

- Seguir recursos en plural y subrecursos para creación/transiciones, como
  `/editions/{id}/registrations` y `/appeals/{id}/acceptance`.
- Recibir records de request y devolver DTOs mediante `XxxDto.from(...)`.
  No serializar directamente agregados, estados o entidades JPA.
- Mantener `ErrorDto {error, details}` y `ApiExceptionHandler`: datos inválidos
  e ids inexistentes → 400; conflictos/transiciones no permitidas → 409;
  equipos inelegibles → 422 con motivos. Conflictos de persistencia → 409.
- Traducir resultados `sealed` en el controller con un switch, preservando sus
  códigos específicos. Consultar las tablas y ejemplos de README antes de
  modificar respuestas; no uniformar automáticamente todo rechazo.
- Conservar rechazo de propiedades JSON desconocidas
  (`fail-on-unknown-properties: true`) y diagnósticos detallados de reglamentos
  o mediciones inválidos. `RulebookBody` convierte el transporte a definiciones;
  el catálogo de reglas sigue en domain.
- El proyecto usa Spring Boot 4 y Jackson con paquetes `tools.jackson` en el
  código existente. Seguir imports actuales y revisar los POM antes de copiar
  ejemplos de otras versiones.

## Cableado y ejecución

- Registrar casos de uso y servicios en `config/UseCaseConfig` sin agregar Spring
  a domain/application.
- `TransactionalUseCases` intercepta beans del paquete exacto
  `com.roboleague.usecase` mediante proxies de clase. Preservar interceptación
  de los métodos y rollback sobre Postgres; comprobarla si cambia el wiring.
- `InMemoryRepositoryConfig` registra teams, editions, rankings y rounds;
  attempts, challenges y appeals usan JPA. Al persistir otro agregado, quitar
  su bean en memoria. No prometer rollback ni durabilidad de todos los agregados.
- `application.yml` configura datasource con `DB_URL`, `DB_PORT`, `DB_USER` y
  `DB_PASSWORD`, desactiva Open Session in View y valida esquema con Hibernate.
  Mantener Flyway como propietario del esquema.
- `demo/DemoConfig` activa `flyway.clean()` con el perfil `demo`.
  `DemoFixture` carga el escenario a través de casos de uso. No activar ese
  perfil para verificar cambios sobre datos que deban conservarse.

## Pruebas y documentación

Los tests de controllers extienden `src/test/java/com/roboleague/api/ApiTest`:
SpringBootTest + MockMvc + Postgres en Testcontainers. Comparten contexto/base;
usar ids propios y no depender de una base vacía ni del orden de ejecución.
Referencias: `appeal/AppealControllerTest`, `challenge/ChallengeControllerTest`
y `TransactionalUseCasesTest` para rollback y proxies.

Verificar códigos, JSON y efectos observables, incluyendo rechazos. Ejecutar
desde la raíz con Docker `mvn -B -pl roboleague-api -am test`; la comprobación
completa de CI es `mvn -B verify`. Actualizar los contratos del README cuando
cambie una ruta o respuesta. Mantener coherencia de la demo si el cambio afecta
los flujos que muestra.
