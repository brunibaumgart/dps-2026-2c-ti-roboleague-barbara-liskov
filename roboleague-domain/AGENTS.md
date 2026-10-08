# Dominio

Leer también `../AGENTS.md`. Código en `src/main/java/com/roboleague/`:
`tournament`, `scheduling`, `evaluation`, `ranking` y puertos en `repository`.
Es Java plano: no introducir dependencias de application/infrastructure/API.

## Invariantes y extensión

- Modificar estado mediante operaciones del agregado que protejan invariantes,
  sin exponer sus componentes mutables para que un consumidor saltee validaciones.
  Un servicio de dominio puede coordinar una regla que no pertenece naturalmente
  a una entidad; no trasladar comportamiento de negocio a un caso de uso por
  comodidad de acceso a los datos.
- Justificar referencias entre agregados por identidad, ciclo de vida y límites
  de consistencia. `Challenge` referencia edición por id, mientras `Edition`
  contiene equipos; no imponer referencias por id a todo el modelo ni convertir
  cada relación en pertenencia al mismo agregado. Cambiar un límite exige revisar
  operaciones, persistencia y pruebas que dependan de él.
- `Edition` organiza el evento; `Challenge` identifica un desafío con reglamento
  propio; `Category` determina elegibilidad. Preservar esta distinción.
- Modelar valores con los records/objetos de valor existentes y validar sus
  invariantes en construcción. Mantener las colecciones expuestas protegidas
  frente a modificaciones externas.
- `Challenge` conserva reglamentos publicados, inmutables y consecutivos desde
  v1. Una modificación publica otra versión, sin reemplazar las anteriores.
- `AttemptId` deriva de slot y número de intento. `RulebookReference` fija el
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
