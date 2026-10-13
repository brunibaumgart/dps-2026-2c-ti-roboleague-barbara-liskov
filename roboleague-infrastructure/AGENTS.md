# Adaptadores de persistencia

Leer también `../AGENTS.md`. Adaptadores en
`src/main/java/com/roboleague/repository/{jpa,memory}`; puertos en domain.
No depender de application o API.

Los adaptadores de soporte `SystemClock` y `UuidGenerator` viven en
`com.roboleague.support`. El primero requiere zona explícita; ambos implementan
puertos de domain y se registran en la configuración API, sin acoplar el núcleo
a sus implementaciones.

## Estado actual y convenciones

- Postgres persiste los siete agregados; los adaptadores en memoria de los siete
  puertos quedan para tests de casos de uso. Los adaptadores de intentos,
  standings, desafíos y apelaciones (mutables) conservan la versión leída por
  instancia y rechazan escrituras concurrentes; `challenges` y `appeals` tienen
  `version` desde la migración `V9`.
- Respetar el contrato observable de cada puerto: identidad, resultado de
  búsquedas, errores y efectos de guardar. Las consultas no deben efectuar
  transiciones de negocio ni escrituras inesperadas. Documentar diferencias de
  concurrencia, durabilidad y rollback frente al adaptador en memoria; no exigir
  equivalencia transaccional que este no implementa. Cubrir los comportamientos
  compartidos del contrato y las garantías particulares del adaptador.
- Mantener entidad JPA propia, interfaz `SpringDataXxx`, mapper en ambos sentidos
  y `JpaXxxRepository implements XxxRepository`. Entidades y detalles de JSON
  quedan dentro del adaptador; no anotar agregados con JPA/Jackson.
- Rehidratar con `restore(...)` (o el constructor completo de `Team`). Conservar
  estado, ids, tiempos, métricas, entregas, revisiones y eventos originales sin
  recrear acciones de negocio.
- ChallengeRepository.findByEditionId filtra por edición y ordena por ChallengeId
  tanto en memory como en JPA. SpringDataChallenges hace la consulta por edition_id;
  el mapper preserva todos los reglamentos, igual que en findById. Consultar una
  edición inexistente es validación de application, no del adaptador.
- `ChallengeMapper` guarda definiciones y reconstruye con `RuleCatalog`.
  Un reglamento persistido inválido es un error; no omitirlo ni sustituirlo por
  un reglamento por defecto. Preservar todas sus versiones.
- Los JSONB de historial y reglamentos son contratos persistidos. Al cambiar
  su forma o discriminadores, considerar lectura de datos existentes y migración.
  Ejemplo: cada versión de `standings` guarda `rankedWith` y `V10` lo completó
  en las filas anteriores.
- `JpaAttemptRepository`, `JpaStandingsRepository`, `JpaChallengeRepository` y
  `JpaAppealRepository` conservan la versión leída fuera del agregado y usan
  `saveAndFlush` con control optimista. Preservar rechazo de escrituras obsoletas
  y creaciones concurrentes del mismo agregado; no convertirlas en sobrescrituras.
- Todo `save` JPA pasa por `WriteConflicts`: versión vieja o clave repetida →
  `ConcurrentModificationConflict` con la causa; otra violación de integridad
  se propaga (500). Todo `toDomain` pasa por `CorruptDataException.rebuilding`:
  un dato guardado irreconstruible es `CorruptDataException`, nunca IAE/ISE.

## Team, Edition y Registration

TeamRepository es canónico y ya no consulta categoría: pertenece a Registration.
EditionRepository.findByTeamId busca ediciones con esa relación; guardar una
nueva versión inmutable reemplaza el snapshot del mismo id. findAll y
findByTeamId ordenan por id en memory y en JPA. Un getter no permite modificar
el objeto guardado.

Tablas `editions` (V6) y `teams` (V7): columnas para cabecera/perfil y JSONB para
torneo, categorías e inscripciones, o robot, integrantes y documentación.
`EditionMapper` usa Edition.restore con TeamId/EditionId/CategoryId,
referenceDate y registeredAt; `TeamMapper` reconstruye Team con profile, robot,
members y Documentation.restore, sin repetir verify. No replay de inscripción
ni consulta del reloj al leer.

## Round y Slot

RoundRepository guarda el agregado completo e incluye findById, findByScope,
findByChallengeId, findAll y findBySlotId (orden por RoundId). Ambos adaptadores
reemplazan el snapshot del mismo RoundId sin duplicar slots y rechazan scopes o
SlotIds usados por otra ronda. Tabla `rounds` (V8): scope y estado en columnas,
slots en JSONB con pista, jueces, ventana, trackInterval y estado; Round.restore
y Slot.restore los rehidratan. Postgres asegura el scope único
(desafío/categoría/número); el SlotId global se comprueba con una consulta del
adaptador. No crear un repositorio de Slot independiente.

## Versiones de agregados inmutables

Edition, Team y Round son inmutables: lo guardado nunca es la instancia leída,
así que no se guarda la versión por instancia como en intentos. Su `save` toma
la versión de la fila que el contexto de persistencia leyó en la misma
transacción y usa `saveAndFlush`: si otro pedido la guardó en el medio, se
rechaza con `ConcurrentModificationConflict` (409, vía `WriteConflicts`). Si la fila no se leyó en la
transacción, se escribe sobre la versión actual. No se coordinan recursos entre
programaciones concurrentes de rondas distintas.

## Esquema y selección de adaptadores

Flyway usa `src/main/resources/db/migration/V<n>__<descripcion>.sql`.
Agregar una nueva migración para cambios de esquema; no editar migraciones
ya integradas. Hibernate usa `ddl-auto=validate`; no reemplazarlo por generación
automática para ocultar desajustes.

La API descubre los `@Repository` JPA por escaneo y no registra adaptadores en
memoria. Evitar dos beans para el mismo puerto. Al cambiar un contrato, revisar
también el adaptador en memoria, utilizado por los tests de casos de uso. Los de
`Attempt`, `Appeal`, `Standings` y `Challenge` (agregados mutables) guardan y
devuelven copias armadas con `restore`, como una base: un caso de uso que muta
y no guarda no pasa su test.

## Verificación

Tests JPA en `src/test/java/com/roboleague/repository/jpa`: `@DataJpaTest`,
Postgres real con Testcontainers, `PostgresContainer` y configuración de
`InfrastructureTestApplication`. Seguir las importaciones/configuración de un
test vecino para la versión de Spring Boot usada aquí.

Verificar ida y vuelta completa, búsquedas y conservación del historial, no
solo que `save` termina. Para cambios en intentos, revisar las pruebas de
concurrencia de `JpaAttemptRepositoryTest`. Ejecutar desde la raíz con Docker:
`mvn -B -pl roboleague-infrastructure -am test`.
Cambios de wiring o atomicidad requieren además pruebas API; Compose no es
necesario para Testcontainers.
