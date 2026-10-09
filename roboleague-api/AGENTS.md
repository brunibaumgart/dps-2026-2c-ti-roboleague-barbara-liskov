# API y composition root

Leer también `../AGENTS.md`. Aplicación en `com.roboleague.RoboLeagueApplication`;
controllers, DTOs, config y demo bajo `src/main/java/com/roboleague/api`.

## Contratos REST

- Seguir recursos en plural y subrecursos para creación/transiciones, como
  `/editions/{id}/registrations` y `/appeals/{id}/acceptance`.
- Recibir records de request y devolver DTOs mediante `XxxDto.from(...)`.
  No serializar directamente agregados, estados o entidades JPA.
- Validar presencia y formato del transporte y convertirlos a commands/valores
  del núcleo. Invocar casos de uso para operaciones de negocio; no acceder desde
  controllers a repositorios JPA para evitar ese flujo. Las invariantes permanecen
  en domain y la coordinación en application, aunque la API traduzca sus errores.
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

## Inscripción y programación (spec 05)

- Consultas pasan por QueryEditionsUseCase, ListEditionChallengesUseCase,
  QueryRegistrationsUseCase y QueryRoundsUseCase. No consultar repositorios desde
  controllers ni serializar agregados. RegistrationDto resuelve equipo canónico.
- POST inscripción exige team o teamId, exactamente uno; PUT exige categoryId y
  team completo, con id coherente con el path. TeamBody usa Clock del servidor
  para creación/verificación; requests no admiten timestamps de inscripción o
  verificación. verifiedBy omitido/null deja documentación sin verificar (422
  cuando el dominio rechaza elegibilidad). Respuestas incluyen datos de auditoría
  de solo lectura; no asumir que un DTO de respuesta puede enviarse como request.
- EditionDto conserva categories como ids y agrega categoryDetails con límites.
  maxAge omitido/null significa sin edad máxima; minAge es inclusivo y se evalúa
  en años cumplidos al inicio de la edición. La demo admite desde 13 años.
  Dimensiones máximas opcionales en alta conservan default 1000 mm por eje.
- RoundController deriva EditionId del desafío; el body no acepta edición.
  Duraciones son segundos y startTime local ISO sin offset. RoundDto ordena
  slots por horario/TrackId/SlotId. Filtro categoryId ajeno → 400; válido sin
  rondas → 200 con []; los padres de cada consulta deben existir.
- RequestValues comprueba presencia y texto en transporte, usando wrappers para
  distinguir campos numéricos/booleanos faltantes. Las reglas de elegibilidad,
  estado y recursos siguen en dominio. Rechazar elementos null de colecciones
  antes de construir valores, evitando NPE/500 ante JSON inválido.

## Front estático (spec 06, parcial)

- Front en src/main/resources/static, servido por el mismo jar/puerto, sin build
  npm. index.html/styles.css y módulos JS separan HTTP, presentación, traducción
  de contratos, inscripciones, programación y captura/desglose. Mantener estética
  sobria, labels, teclado, foco visible y layout de escritorio. El frontend es
  exclusivamente para escritorio; mantener ese alcance en futuras extensiones.
  Navegación principal en el header; consultas complementarias en el panel
  lateral desplegable, cerrado al inicio. Conservar aria-expanded, cierre con
  Escape y cierre con el mismo botón de menú, sin botón de cruz adicional.
  Cambiar de sección vuelve al inicio de la página; usar preventScroll al mover
  el foco para evitar que el navegador saltee el header y los selectores.
- Consumir API real: no calcular scoring, elegibilidad ni publicación en JS. Los
  recursos sugeridos de otras rondas conservan ids; nuevos ids se generan al crear
  candidatos y no se cambian al editar. No presentar ids como nombres de recursos.
- Renderizar entrada del usuario con nodos/textContent, nunca innerHTML. Conservar
  todos los detalles de ErrorDto. Un 422 conserva el formulario; un 409 exige
  recargar datos antes de otra mutación. No retry automático ni doble submit.
- Resultados consultan breakdown y la versión fijada mediante GET
  /challenges/{id}/rulebook/versions/{version} si difiere de la actual. No usar
  métricas vigentes para una captura histórica. GetRulebookUseCase es de lectura.
- Apelaciones/tabla muestran faltantes reales del frente 4. No marcar completada
  spec 06 ni simular esos flujos mientras no haya contratos y verificación web.
- Tests de módulos JS: node --experimental-default-type=module --test
  roboleague-api/src/test/frontend/*.test.mjs (Node 20+, sin paquetes). Maven
  comprueba entrega de assets y HTTP; no atribuir a estos tests validación visual
  o de interacción de navegador. Registrar pendientes por separado.

## Cableado y ejecución

- Registrar casos de uso y servicios en `config/UseCaseConfig` sin agregar Spring
  a domain/application.
- Ensamblar Clock/IdGenerator en esa configuración. `roboleague.time-zone`
  usa `ROBOLEAGUE_TIME_ZONE`, con default `America/Argentina/Buenos_Aires`.
  Pasar valores explícitos al dominio también desde fixtures de demo; no
  incorporar un reloj global ni usar la zona implícita del host.
- `TransactionalUseCases` intercepta beans del paquete exacto
  `com.roboleague.usecase` mediante proxies de clase. Preservar interceptación
  de los métodos y rollback sobre Postgres; comprobarla si cambia el wiring.
- RegisterTeamUseCase, UpdateTeamUseCase y ChangeRegistrationCategoryUseCase
  están ensamblados en UseCaseConfig y reciben proxies transaccionales. Team y
  Edition siguen en memoria. RegistrationController expone altas, consultas y
  PUT completo mediante UpdateRegistrationUseCase, validando equipo/categoría
  juntos antes de guardar. No encadenar UpdateTeam y ChangeRegistrationCategory
  desde el controller; la transacción no cubre los repositorios en memoria.
- ScheduleRoundUseCase recibe ChallengeRepository y tiene proxy transaccional;
  las rondas siguen en memoria. Captura conserva challengeId en el request por
  compatibilidad y rechaza desafío distinto al de la ronda con 409. El juez
  asignado puede enviar ambas fuentes; las notas del panel no asignan jueces.
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
