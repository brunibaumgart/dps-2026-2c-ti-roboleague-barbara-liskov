# Dominio

Leer también `../AGENTS.md`. Código en `src/main/java/com/roboleague/`:
`tournament`, `scheduling`, `evaluation`, `ranking` y puertos en `repository`.
Es Java plano: no introducir dependencias de application/infrastructure/API.

Creación y transiciones reciben fechas o `OperationAudit` explícitos. Los
agregados no almacenan Clock/IdGenerator; los servicios que generan identidades
(como RoundSchedulerService) reciben el puerto por constructor. Usar
`TeamMember.of(..., referenceDate)` al crear participantes de negocio; su
constructor de valor permite rehidratar sin consultar el calendario actual.

## Inscripción y estado canónico

- Team/Robot/Documentation/Edition son inmutables. Métodos `with...`, verify,
  revokeVerification, addCategory y registerTeam devuelven candidatos o nuevas
  ediciones: conservar el valor devuelto; nunca asumir mutación del original.
- Registration se identifica por EditionId/TeamId. Su categoría y su fecha de
  referencia pertenecen a la edición; la fecha es Edition.startDate. No agregar
  categoría ni fecha de inscripción globales a Team.
- RegistrationEligibility compone reglas sobre EligibilityCandidate (Team,
  Category, fecha explícita). Inscripción, cambio de categoría, actualización y
  programación usan el mismo control. Los rechazos de elegibilidad conservan
  todos los motivos en tournament.eligibility.TeamIneligibleException.
- Cambiar documentos con withDocument invalida su verificación en el candidato;
  verify devuelve otro valor con autor y hora explícitos. Los getters y los
  constructores no deben conservar aliases de listas, mapas o sensores mutables.
- Edition.restore conserva inscripciones y tiempos sin reevaluar equipos ni
  consultar el reloj; valida ids de edición, categorías, fecha y duplicados.
  Team se rehidrata con su constructor completo; Documentation.restore conserva
  documentos y metadatos de verificación. Estas vías son para adaptadores.

## Rondas y recursos

- Round/Slot son inmutables. addSlot, start, complete, startSlot, completeSlot y
  cancelSlot devuelven otra Round; conservarla. Slot no tiene mutadores públicos.
  Round protege pertenencia, ids/equipos únicos, recursos sin solapamientos y
  transiciones; completar exige slots completados o cancelados.
- RoundScope incluye desafío, edición, categoría y número positivo. El caso de
  uso verifica pertenencia del desafío y categoría y revalida inscripciones.
- RoundSchedulerService elige la primera combinación pista/juez libre más
  temprana, desempata por orden de recursos y asigna un juez por slot. Recursos
  se comparan por id. Pistas inactivas se excluyen; ids duplicados se rechazan.
- Ventanas [inicio, fin): jueces/equipos se liberan al fin, pistas tras
  Slot.trackInterval. Conservar esa pausa para comparar rondas distintas.
  CANCELLED libera recursos; COMPLETED conserva la ocupación histórica.
- Round.restore y Slot.restore preservan identidad, asignaciones, horarios,
  pausa y estados sin repetir transiciones ni generar ids. Validan estructura;
  no usarlos como entrada de negocio para saltear el ciclo de vida.
- El lifecycle de ronda/slot es independiente de las fuentes recibidas y del
  puntaje de Attempt. No completar turnos automáticamente al recibir mediciones.

## Invariantes y extensión

- Modificar estado mediante operaciones del agregado que protejan invariantes,
  sin exponer sus componentes mutables para que un consumidor saltee validaciones.
  Un servicio de dominio puede coordinar una regla que no pertenece naturalmente
  a una entidad; no trasladar comportamiento de negocio a un caso de uso por
  comodidad de acceso a los datos.
- Justificar referencias entre agregados por identidad, ciclo de vida y límites
  de consistencia. `Challenge` referencia edición por id, mientras `Edition`
  contiene Registration con TeamId; no imponer referencias por id a todo el modelo ni convertir
  cada relación en pertenencia al mismo agregado. Cambiar un límite exige revisar
  operaciones, persistencia y pruebas que dependan de él.
- `Edition` organiza el evento; `Challenge` identifica un desafío con reglamento
  propio; `Category` determina elegibilidad. Preservar esta distinción.
- Modelar valores con los records/objetos de valor existentes y validar sus
  invariantes en construcción. Mantener las colecciones expuestas protegidas
  frente a modificaciones externas.
- `Challenge` conserva reglamentos publicados, inmutables y consecutivos desde
  v1. Una modificación publica otra versión, sin reemplazar las anteriores.
- Las identidades de equipos, participantes, robots, categorías y ediciones
  viven en tournament; las de jueces, pistas, rondas y slots en scheduling;
  RankingId y AppealId en sus contextos. Todas conservan el texto y rechazan
  null/blanco. `Ranking` expresa el filtro de ronda con `Optional<RoundId>`:
  vacío significa todas las rondas; no construir un RoundId vacío.
- `AttemptId` contiene `SlotId` y número de intento. `RulebookReference` fija el
  desafío y versión usados: capturas posteriores y correcciones deben conservarlos.
- `Attempt.receive` valida la fuente y las métricas antes de aceptar la captura;
  una captura rechazada no cambia el agregado. No puntuar hasta recibir todas
  las fuentes requeridas ni reemplazar silenciosamente una fuente ya recibida.
- Mantener `countableScore()` vacío para intentos sin puntaje computable o
  descalificados. No convertir ausencia de puntaje en un cero ficticio.
- Preservar las revisiones y eventos de auditoría, con versión de reglamento,
  autor, razón y tiempos. Las correcciones agregan revisiones; no reescriben historia.
- Respetar las transiciones de `AttemptState` y `AppealState`, incluyendo varias
  apelaciones abiertas. `restore(...)` es para rehidratar persistencia sin repetir
  transiciones, generar eventos o recalcular el historial.
- `ScoreBreakdown` explica el total mediante sus ítems, incluyendo el piso en
  cero. El tope de bonificaciones se aplica al conjunto de bonos; preservar la
  coherencia entre total, secciones y contribuciones por fuente.

## Reglas y rankings

Una nueva regla va en `evaluation/rules`, implementando `BaseRule`, `BonusRule`
o `DeductionRule` según corresponda. Seguir el patrón `TYPE`, constantes de
parámetros, `from(RuleDefinition)`, `definition()` y `metrics()`. Registrar el
tipo en su sección de `RuleCatalog.standard()`; la API y la persistencia
reconstruyen reglamentos usando ese mismo catálogo.

Mantener rechazo de tipos/parámetros desconocidos, secciones incorrectas y
métricas no declaradas. No duplicar el catálogo en controllers o mappers.
Al extender reglas, revisar `RuleCatalogTest.everyRuleType()`,
`ScoreRuleSectionsTest` y la tabla de reglas del README.

Hay estrategias de rondas y desempate en `evaluation/scheme` y cálculo de tablas
en `ranking`, con cadenas en `ranking/tiebreakers`. Leer sus consumidores antes
de cambiar criterios: no asumir que todo ranking usa automáticamente el esquema
publicado del desafío.

## Verificación

Tests en `src/test/java/com/roboleague`, con JUnit, AssertJ y Mockito según el
caso. Ejecutar desde la raíz `mvn -B -pl roboleague-domain test`.
Probar el comportamiento modificado y sus límites: rechazo sin mutación,
versionado, desglose, estados, auditoría o desempates según corresponda.
Si cambia una definición serializable o un puerto, verificar sus consumidores
en application, infrastructure y API, además del dominio.
