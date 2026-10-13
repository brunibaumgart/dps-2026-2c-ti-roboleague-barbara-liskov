# Documento de Decisiones de Diseño (DESIGN.md) - RoboLeague

**Trabajo Integrador - Diseño del dominio y evolución a aplicación REST**

**Plataforma de Competencias de Robótica y Desafíos Técnicos**

---

## 1. Arquitectura y organización del dominio

El documento se inició en la Entrega 1, centrada en el dominio, y conserva
decisiones de esa etapa junto con su evolución. Actualmente RoboLeague es una
aplicación Maven multimódulo con arquitectura **hexagonal (puertos y adaptadores)**,
modelado inspirado en **DDD** y separación compatible con la regla de dependencias
de **Clean Architecture**.

Cómo leerlo: el capítulo 2 registra cada decisión en el orden en que se tomó
(las secciones 2.1 a 2.7 vienen de la Entrega 1 y anotan qué cambió después).
El capítulo 6 resume, para cada funcionalidad nueva de la Entrega 2 (F1, F2 y
F3), las clases agregadas y modificadas, las refactorizaciones y la deuda
técnica que se dejó a propósito; el capítulo 7 reúne en una tabla los patrones
y técnicas que se decidió no aplicar y sus consecuencias.

| Módulo | Responsabilidad y dependencia de producción |
| --- | --- |
| `roboleague-domain` | Modelo, invariantes, servicios de dominio y puertos; Java sin frameworks |
| `roboleague-application` | Casos de uso que coordinan el dominio; depende de domain |
| `roboleague-infrastructure` | Adaptadores JPA/Postgres de los siete agregados y adaptadores en memoria para tests de casos de uso; depende de domain |
| `roboleague-api` | REST, DTOs, configuración Spring y ensamblado; depende de application e infrastructure |

Application depende de infrastructure únicamente en tests. En ejecución, un
controller llama al caso de uso, que utiliza el dominio y los puertos de
repositorio. En el código, el adaptador de persistencia depende del contrato
interno que implementa; el núcleo desconoce JPA y HTTP. Los métodos públicos de
casos de uso constituyen la entrada al núcleo sin requerir una interfaz por clase.
La configuración de Spring vive en API, incluyendo las transacciones.

Esta separación permite probar el negocio con Java y repositorios en memoria,
incorporar REST y persistencia por etapas y extender reglas de puntuación sin
acoplarlas al transporte o al esquema de datos. Su costo es mantener mappers,
contratos y configuración adicionales; las nuevas abstracciones deben responder
a variaciones o límites concretos del negocio.

DDD aparece en el lenguaje del modelo, objetos de valor, agregados con
comportamiento, servicios, repositorios y especificaciones. El dominio se
organiza en cuatro áreas que reflejan el flujo de una competencia técnica:

```text
roboleague-domain/
├── tournament/          # Eventos, Ediciones, Categorías, Equipos, Elegibilidad
├── scheduling/          # Rondas, Turnos (Slots), Pistas, Jueces
├── evaluation/          # Intentos, Métricas capturadas, Reglas de Scoring, Auditoría
└── ranking/             # Criterios de ordenamiento, Desempates, Publicación, Apelaciones
```

Estas áreas comparten tipos y referencias. La división por paquetes no demuestra
bounded contexts independientes: delimitar uno requiere establecer dónde es
válido su modelo y cómo se integra con otros. Tampoco implica microservicios.

### Responsabilidades por área:
1. **`tournament`**: Modela el ciclo organizativo: definición de temporadas, torneos y ediciones cronológicas; categorización técnica con restricciones físicas y etarias; registro de equipos, participantes y especificaciones de robots; y evaluación compuesta de elegibilidad.
2. **`scheduling`**: Modela la logística operativa de campo: gestión de pistas o arenas de prueba, designación y perfiles de jueces evaluadores, planificación de rondas y asignación determinista de turnos (slots) con ventanas temporales y pausas intermedias.
3. **`evaluation`**: Corazón computacional del sistema. Modela los intentos en pista (`Attempt`), la captura estructurada de métricas observadas (`RawMetrics`), el motor de puntuación explicable y versionado (`Rulebook`, `ScoreRule`, `ScoreBreakdown`), y el rastro de auditoría append-only con snapshots inmutables y eventos de dominio.
4. **`ranking`**: Consolida los puntajes de cada equipo en una categoría de un desafío (`Standings`), resuelve empates con la cadena declarada en el `RankingScheme` del reglamento, versiona y publica la tabla (`StandingsVersion` / `OfficialPublication`), y gobierna el ciclo de apelaciones (`Appeal`) mediante una máquina de estados polimórfica.

**Cardinalidad del evento:** una `Edition` es el evento y tiene varios `Challenge` (Laberinto, Seguidor de línea, Rescate). Cada desafío publica su propio reglamento versionado (`Rulebook`). `Category` es elegibilidad, no un desafío. Ver 2.8.

### Criterios SOLID y límites del modelo

- **SRP:** controllers traducen HTTP, casos de uso coordinan, dominio mantiene
  invariantes y cálculos, y mappers convierten representaciones persistidas.
  Separar responsabilidades por sus razones para cambiar.
- **OCP:** Strategy, Composite y Specification permiten extender comportamiento
  mediante composición. Agregar una regla y registrarla en `RuleCatalog` es una
  extensión válida; no exige mantener intacto todo archivo de configuración.
- **LSP:** implementaciones de reglas y puertos deben respetar sus contratos
  observables. Los adaptadores en memoria y JPA no son equivalentes en durabilidad,
  concurrencia o rollback; sus diferencias deben ser explícitas.
- **ISP:** mantener contratos enfocados en sus consumidores; no obligar a un
  adaptador a simular operaciones ajenas o rechazar métodos prometidos por su interfaz.
- **DIP:** application depende de puertos del núcleo y recibe implementaciones
  por constructor; el contenedor Spring ensambla, pero no define por sí solo esta inversión.

Las invariantes se protegen mediante operaciones del agregado y construcción de
objetos de valor, incluso sin HTTP. API valida el transporte y application los
requisitos de coordinación. Las referencias entre agregados requieren revisar
identidad, ciclo de vida y consistencia: `Challenge` referencia edición por id,
mientras `Edition` contiene Registration y referencia equipos canónicos por TeamId. No se impone una política universal de ids.

---

## 2. Decisiones de Diseño Detalladas y Análisis de Trade-offs

### 2.1 Reificación de Conceptos de Negocio en Objetos de Valor (Value Objects)

- **Problema**:  
  En sistemas de competencias técnicas es habitual caer en la *obsesión por primitivos*, transportando números o cadenas sueltas (ej. peso en gramos, largo/ancho/alto en milímetros, edades mínimas y máximas, notas de jueces, timestamps). Esto dispersa las validaciones, promueve firmas de métodos y constructores inmanejables, y deslocaliza la lógica hacia clases de servicio o utilitarias.

- **Solución Implementada**:  
  Se reificaron todos los conceptos de negocio mediante **Records y Value Objects inmutables**:
  - *Restricciones Físicas y de Hardware*: `Weight`, `Dimensions`, `RobotHardware` y `RobotLimits`.
  - *Restricciones de Competencia*: `AgeRange`, `TeamSizeRange`, `CategoryRestrictions` y `DateRange`.
  - *Logística y Planificación*: `TimeWindow`, `SlotIdentity`, `SlotAssignment`, `RoundScope`, `RoundInfo`, `RoundScheduleTiming`, `RoundResources` y `RoundScheduleRequest`.
  - *Métricas y Puntuación*: `Points`, `TimeTargets`, `TimeAdjustments`, `TimeRuleConfig`, `TrackPerformance`, `EvaluationFeedback`, `EvaluationDetails`, `MetricDefinition` y `ValueRange`.
  - *Clasificación*: `TeamIdentity`, `StandingsId`, `Placement`, `StandingsEntry`, `TeamRounds` y `RoundResult`.
  - *Auditoría y Reclamos*: `SnapshotIdentity`, `AuditAuthor`, `SnapshotMetadata`, `EvaluationSnapshot`, `AppealTarget` y `AppealClaim`.

  La lógica de dominio se ubicó estrictamente donde reside el dato:
  - `RobotLimits.checkViolations(spec)` explica cada límite de masa y volumen que el robot excede.
  - `AgeRange.contains(int age)` y `TeamSizeRange.allows(int count)` validan las reglas de admisión; las especificaciones de elegibilidad delegan en ellos (2.6, 2.29).
  - `DateRange.includes(LocalDate)` decide si una ronda cae dentro de la edición (`Round.happensWithin`).
  - `Points.compareTo` decide la igualdad de puntajes sin depender del orden de la suma (2.28), y cada `TieBreakCriterion` compara su propia métrica de `ChallengeScore` (2.10). En la Entrega 1 lo hacían `PerformanceSummary.isTiedWith` y `TeamScore.isTiedWith`, que se eliminaron con la tabla vieja (2.26).

- **Pros**:
  - **Invariantes Garantizadas en el Origen**: Un objeto no puede existir en estado inconsistente (ej. peso negativo, dimensiones invertidas, rangos con mínimo mayor que máximo).
  - **Auto-documentación y Expresividad**: El código habla el lenguaje ubicuo de la robótica en lugar de tipos genéricos (`double`, `String`, `int`). Los puntajes son `Points` (2.28); las mediciones crudas y los factores de las reglas siguen siendo `double`.
  - **Alta Cohesión y Localidad**: La lógica de validación y cálculo viaja junto a sus propios datos, eliminando clases "anémicas" y utilitarios estáticos externos.
  - **Firmas Limpias y Cohesivas**: Los constructores de entidades y agregados reciben pocos parámetros semánticamente ricos y fuertemente tipados.

- **Contras**:
  - **Mayor Cantidad de Tipos**: Incremento en la cantidad de clases y records en el proyecto.
  - **Sobrecarga de Instanciación**: Creación de más objetos en memoria que el uso directo de tipos primitivos (despreciable en entornos de ejecución JVM modernos).

---

### 2.2 Motor de Puntuación Explicable, Componible y Versionado (Strategy + Composite)

- **Problema**:  
  Cada disciplina (carrera de velocidad, laberinto autónomo, sumo de robots, rescate) calcula puntajes de manera radicalmente distinta: unas premian la reducción de tiempo, otras bonifican hitos o penalizan colisiones, y otras ponderan apreciaciones cualitativas de jueces. Hardcodear el cálculo en un método de servicio o mediante bifurcaciones condicionales viola el principio de Abierto/Cerrado (OCP) y hace imposible explicar la procedencia de una calificación a los participantes ante un reclamo.

- **Solución Implementada**:  
  - **Patrón Strategy**: Interfaz `ScoreRule` que desacopla el cálculo de la puntuación en estrategias independientes.
  - **Patrón Composite**: `CompositeScoreRule` permite agrupar reglas elementales que leen de la misma fuente (por ejemplo "Desempeño en pista" = Tiempo + Objetivos) y evaluarlas como una única unidad compuesta. El reglamento no es un composite: su `ScoringScheme` guarda listas de reglas que pueden mezclar fuentes (ver 2.11 y 2.12).
  - **Explicabilidad mediante Value Objects (`ScoreBreakdown` y `ScoreItem`)**: Al evaluar un intento en pista (`Attempt`), la regla no retorna un número escalar (`double`), sino un desglose inmutable que detalla:
    - Concepto evaluado (`concept`).
    - Métrica observada (`rawMetric`).
    - Fórmula matemática aplicada con sus coeficientes (`appliedFormula`).
    - Subtotal parcial calculado (`subtotal`).
    - Notas y justificaciones reglamentarias.
  - **Reglamento inmutable y versionado por desafío (`Rulebook`)**: Cada `Challenge` guarda el historial de sus reglamentos; publicar crea la versión siguiente (`v1`, `v2`…) y nunca modifica la anterior (ver 2.8). Esa versión se aplica cuando nace el puntaje (captura del intento o apelación aceptada). El recálculo de la tabla no vuelve a interpretar las métricas: reordena posiciones a partir del último snapshot, que ya fue evaluado con ese reglamento (ver 2.9).
  - **Inmutabilidad real, no de superficie (Entrega 2, hallazgo 1)**: `CompositeScoreRule` guarda `List.copyOf` de sus reglas y no tiene `addRule`. Antes el record `ScoringPolicy` era inmutable solo en la referencia: el composite que contenía se podía editar y el puntaje cambiaba sin cambiar la versión. Un conjunto de reglas distinto es otro reglamento.
  - **El desglose explica el total**: el total de `ScoreBreakdown` es la suma exacta de sus ítems en `Points` (2.28); no guarda un total aparte que pueda diferir. El piso en cero ya no se aplica en silencio: aparece como ítem `Piso en cero` con fórmula `max(0, suma)`. Se descartó un constructor privado porque un record no admite constructor canónico privado; validar en el constructor compacto da la misma garantía.
  - **Las reglas validan sus parámetros**: `PenaltyRule`, `ResourceConsumptionRule`, `JudgeSubjectiveRule`, `TimeAdjustments`, `FaultTariff`, `VictimTariff`, `PrecisionRule`, `MilestoneBonusRule` y `CappedAt` rechazan valores negativos (una penalización con deducción negativa sumaba puntos). Para el código que arma reglas, un parámetro negativo es una invariante rota y la regla lanza `IllegalArgumentException` (regla #28). Para un reglamento que llega por la API es una falla esperable: `RuleCatalog` convierte esa excepción en un resultado `Rejected` con el problema (ver 2.13).

- **Pros**:
  - **Transparencia Forense Inmediata**: Cualquier participante o árbitro puede auditar paso a paso cómo se compuso cada punto del intento.
  - **Extensibilidad sin Modificación (OCP)**: Añadir un nuevo tipo de desafío (ej. eficiencia energética o algoritmos de visión) solo requiere implementar una nueva clase `ScoreRule` sin tocar el motor central.
  - **Inmutabilidad Histórica**: Modificar las reglas para la edición 2027 no afecta ni altera los resultados ya procesados de la edición 2026.

- **Contras**:
  - **Estructura de Datos Heterogénea**: `RawMetrics` debe suministrar datos suficientes para alimentar a distintas reglas activas o manejar opcionalidad de métricas no registradas en ciertas categorías.
  - **Costo de Modelado**: Mayor volumen de clases y estructuras auxiliares que una simple función matemática de una línea.

---

### 2.3 Trazabilidad No Destructiva y Auditoría (Append-Only Log con Snapshots y Eventos)

- **Problema**:  
  La consigna del dominio establece: *"Los resultados nunca se sobreescriben directamente. Conservar los valores originales y todas las modificaciones."* Si un juez comete un error al cargar faltas o si una apelación modifica una medición, actualizar una variable in-place destruiría la evidencia y violaría el principio de auditoría.

- **Solución Implementada**:  
  - Se implementó un esquema de **Append-Only Log** en el aggregate `Attempt`.
  - Cada cambio sobre un intento genera una revisión inmutable (`AttemptScoreSnapshot`) que contiene:
    - Número secuencial de revisión (`revisionNumber`).
    - Metadatos de auditoría: timestamp de modificación, identificador y rol del autor (`AuditAuthor`).
    - Métricas brutas registradas en esa revisión (`RawMetrics`).
    - Desglose detallado del puntaje resultante (`ScoreBreakdown`).
    - Motivo justificado del ajuste (`reason`).
  - Adicionalmente, el aggregate emite y almacena eventos de dominio inmutables (`ResultRegisteredEvent`, `SourceReceivedEvent`, `ScoreAdjustedEvent`, `PenaltyAppliedEvent`, `AppealAcceptedEvent`, `AttemptDisqualifiedEvent`); desde 2.18 `AttemptEvent` es sellado.
  - La entidad expone el historial completo (`getRevisionHistory()`, cuya primera revisión es la original) y la última vigente (`getLatestSnapshot()`).

- **Pros**:
  - **Auditoría Forense Completa**: Es posible reconstruir el estado exacto del intento en cualquier momento de su ciclo de vida.
  - **Imposibilidad de Manipulación Silenciosa**: Cualquier corrección requiere autor, motivo y genera una nueva entrada inmutable.
  - **Soporte para Recálculos Históricos**: Ante la rectificación de un resultado por parte del comité de arbitraje, la tabla de posiciones se recalcula de forma determinista basándose en la última fotografía auditada.

- **Contras**:
  - **Crecimiento del historial**: La lista de snapshots y eventos acumula objetos a lo largo de la vida del aggregate. Desde 2.18 se guarda en columnas JSONB de la fila del intento, que se lee y escribe entera: un intento con muchas correcciones se vuelve más pesado de cargar.
  - **Mayor Complejidad de Consulta**: Acceder al puntaje actual implica consultar el snapshot vigente (`latestSnapshot`) en vez de un atributo primitivo directo en la raíz de la entidad.

---

### 2.4 Clasificación y cadenas de desempate (de Comparator Composition a criterios del reglamento)

- **Problema**:  
  Los torneos de robótica cuentan con reglas de desempate complejas que varían según la categoría. Por ejemplo: si dos robots igualan en puntos totales, desempata el menor tiempo; si empatan en tiempo, desempata la menor cantidad de penalizaciones; luego la puntuación de jueces. Acoplar estas prioridades en un bloque monolítico de sentencias `if-else` hace que el algoritmo sea frágil y difícil de testear en aislamiento. Además, cada posición tiene que poder explicar qué criterio la decidió.

- **Entrega 1 (reemplazada)**: `TieBreakerChain` componía comparadores atómicos (`Comparator<TeamScore>`: mayor total, menor tiempo, menos penalizaciones, mayor nota de jueces y, al final, el id del equipo) y `RankingCalculatorService` asignaba posiciones con un `TieStatus`. Tenía dos límites que la Entrega 2 hizo visibles: la cadena se armaba en código (`Main`), fuera del reglamento, así que dos desafíos no podían desempatar distinto ni reproducir el criterio de una versión anterior (hallazgo 8); y un `Comparator` devuelve un número, no qué criterio decidió, por lo que la explicación del empate era siempre el mismo texto.

- **Solución actual (Entrega 2)**:
  - La cadena es dato del reglamento: `RankingScheme` (2.10) guarda la selección de rondas y una lista ordenada de `TieBreakCriterion`, cada uno una clase con nombre de dominio (`HigherTotal`, `LowerTime`, `LowerDeductions`, `HigherJudgeScore`) y un `code()` estable para la API (`higher-total`…).
  - `RankingScheme.decide(a, b)` recorre la cadena y devuelve un resultado sellado, `TieBreakDecision.DecidedBy(criterio, orden)` o `Tied`; `compare` se apoya en `decide`. Así cada posición sabe qué criterio la separó sin un `if` sobre el tipo de criterio.
  - `StandingsTable.rank(esquema, equipos)` (2.26) ordena los `ChallengeScore` de cada equipo con ese esquema y deja en cada `Placement` la explicación ("Empata en puntaje con el puesto 1; desempata por Menor tiempo"). El desempate final por id de equipo se conserva con `Comparable<TeamId>` (2.21).
  - `TieBreakerChain`, `RankingCalculatorService`, `TeamScore`, `RankingEntry` y `TieStatus` se eliminaron (2.26).

- **Pros**:
  - **Flexibilidad configurable por desafío**: el orden de los criterios se declara al publicar el reglamento y viaja con su versión.
  - **Testeabilidad aislada**: cada criterio se prueba por separado (`RankingSchemeTest`) y la tabla con la cadena completa (`StandingsTableTest`).
  - **Claridad explicativa**: la posición nombra el criterio que la decidió.

- **Contras**:
  - **Dependencia de la información de entrada**: un criterio que mire otra métrica exige exponerla antes en `ChallengeScore`.
  - **Una clase por criterio**: agregar uno es una clase nueva y una entrada en `RuleCatalog`.

---

### 2.5 Máquina de Estados Polimórfica para Apelaciones (State Pattern)

- **Problema**:  
  Las apelaciones de los equipos atraviesan un flujo legal estricto: presentación (`Pending`) -> revisión técnica por comité (`UnderReview`) -> resolución final (`Accepted` o `Rejected`). Resolver estas transiciones y sus restricciones mediante un campo `enum` o `String` y condicionales dispersos (`if (appeal.getStatus().equals("PENDING"))`) provocaría:
  1. Riesgo de transiciones ilegales (ej. aceptar un reclamo directamente sin pasar por revisión técnica, o modificar una apelación ya rechazada).
  2. Duplicación de lógica condicional en múltiples casos de uso.
  3. Acoplamiento a identificadores textuales o flags de estado.

- **Solución Implementada**:  
  - Se implementó el **Patrón State** puro con jerarquía polimórfica:
    - Interfaz `AppealState` (`beginReview`, `accept`, `reject`).
    - Estados concretos: `PendingAppealState`, `UnderReviewAppealState`, `AcceptedAppealState`, `RejectedAppealState`.
  - Cada estado encapsula sus transiciones válidas y lanza `IllegalStateException` con un mensaje expresivo ante operaciones no permitidas en su fase actual; la API la traduce a 409.
  - `AppealState` ofrece **consultas polimórficas**: `isPending()`, `isUnderReview()`, `isAccepted()`, `isRejected()` e `isResolved()`, que `Appeal` expone sin preguntar el tipo del estado.
  - **Qué bloquea la publicación (Entrega 2)**: la versión de la Entrega 1 preguntaba a cada apelación si permitía publicar, con un caso de uso y un método de estado que ya no existen. Ahora el intento cuenta sus apelaciones abiertas en su propio estado (`UnderAppealAttemptState`, 2.18), `PublicationCheck.of(tabla, rondas, intentos)` suma esas apelaciones y los turnos sin resultado, y `Standings.publish` decide con ese chequeo y devuelve un resultado sellado (`StandingsPublication.Published` o `Blocked` con cada motivo). El caso de uso no tiene ningún `if` de estado:
    ```java
    StandingsPublication publication = calculated.publish(version, results.read(id).publicationCheck(), note,
            clock.now());
    switch (publication) {
        case StandingsPublication.Published published -> standings.save(calculated);
        case StandingsPublication.Blocked blocked -> {
        }
    }
    ```
    (`PublishStandingsUseCase`; el controller traduce `Published` a 200 y `Blocked` a 409 con los motivos.)

- **Pros**:
  - **Eliminación Total de Condicionales de Tipo o Estado**: Desaparecen los `switch` y los `equals` de strings para preguntar el estado.
  - **Inviolabilidad de Transiciones**: Es arquitectónicamente imposible aceptar un reclamo pendiente sin haberlo puesto en revisión previa, o modificar una apelación en estado terminal.
  - **Extensibilidad Segura**: Agregar un estado intermedio (ej. `EscalatedToFederationState`) requiere implementar la interfaz sin tocar el código de los demás estados.

- **Contras**:
  - **Mayor Cantidad de Clases**: 4 clases concretas más la interfaz en lugar de una simple enumeración.
  - **Conversión al persistir**: `AppealMapper` es el único lugar que elige un estado por su nombre guardado; `Appeal.restore` recibe un `AppealProgress` y reconstruye sin repetir transiciones. Un estado nuevo exige sumarlo ahí.

---

### 2.6 Verificación Compuesta de Elegibilidad (Composite Specification Pattern)

- **Problema**:  
  Validar si un equipo puede inscribirse en una categoría involucra múltiples dimensiones ortogonales: edades de los integrantes, cantidad mínima y máxima de miembros, peso y dimensiones del robot, y documentación aprobada. Poner estas comprobaciones en `RegisterTeamUseCase` o dentro de `Team` sobrecargaría de responsabilidades al agregado o al servicio de aplicación.

- **Solución Implementada**:  
  - Se implementó el patrón **Specification** componible mediante la interfaz genérica `EligibilitySpecification<T>`.
  - Las reglas evalúan EligibilityCandidate (Team, Category y fecha explícita).
    RegistrationEligibility reúne la política estándar, reutilizada en inscripción,
    actualización, cambio de categoría y programación; la referencia es el inicio
    de la edición.
  - Composición con `and()`, que conserva los motivos de ambas reglas. `or()` y `not()` se eliminaron: ninguna política los usaba (2.29).
  - Especificaciones atómicas de dominio:
    - `AgeLimitSpecification`: Comprueba el rango etario de los integrantes calculando la edad exacta a la fecha del torneo; delega el rango en `AgeRange.contains`.
    - `TeamSizeSpecification`: Comprueba que el total de miembros cumpla los límites de la categoría; delega en `TeamSizeRange.allows`.
    - `RobotSpecificationLimit`: Comprueba que masa y dimensiones del robot no violen los topes de la categoría delegando en `RobotLimits`.
    - `DocumentationVerifiedSpecification`: Comprueba que la documentación técnica requerida haya sido revisada y verificada.
  - `EligibilityResult` reporta el veredicto booleano junto con la lista detallada de causas de rechazo en lenguaje de dominio.

- **Pros**:
  - **Composabilidad Declarativa**: Permite armar conjuntos de reglas distintos para torneos colegiales, universitarios o internacionales simplemente encadenando `.and(...)`.
  - **Feedback Detallado y Explicativo**: Si un equipo es rechazado, se le devuelve la lista exacta de motivos (ej. *"Robot weight (2600g) exceeds category limit (2500g)"*, *"Team member age (27) exceeds maximum age (25)"*).
  - **Testeabilidad Unitaria Independiente**: Cada especificación se prueba de manera aislada con instancias mínimas, sin mocks.

- **Contras**:
  - **Costo de Evaluación**: Se evalúan múltiples reglas sobre colecciones en memoria, lo cual para listas muy masivas de equipos podría requerir optimizaciones de cortocircuito (aunque en competencias presenciales el volumen de equipos es perfectamente manejable).

---

### 2.7 Inversión de Control, Aislamiento del Dominio y Composition Root

- **Problema**:  
  Si los casos de uso o servicios de dominio crean internamente instancias concretas (`new InMemoryRepository()`, `new ConcreteService()`), quedan fuertemente acoplados a detalles de infraestructura. Del mismo modo, si el dominio importa herramientas de consola (`System.out`), frameworks de serialización (`Gson`) o clientes HTTP (`Unirest`), el negocio pierde pureza y portabilidad.

- **Solución Implementada**:  
  - **Puertos internos**: Los casos de uso conocen contratos de persistencia definidos en domain: `TeamRepository`, `EditionRepository`, `RoundRepository`, `ChallengeRepository`, `AttemptRepository`, `AppealRepository` y `StandingsRepository` (en el paquete `repository`, con `ConcurrentModificationConflict` como falla de escritura concurrente). No dependen de los adaptadores JPA o en memoria. `RankingRepository` era el puerto de la Entrega 1 y se reemplazó por `StandingsRepository` (2.26).
  - **Inyección por Constructor**: Los casos de uso reciben puertos y servicios; construir objetos del modelo dentro del negocio es válido. Evitar la creación interna de adaptadores tecnológicos.
  - **Composition Root actual**: [`UseCaseConfig`](roboleague-api/src/main/java/com/roboleague/api/config/UseCaseConfig.java) registra servicios y casos de uso. Spring descubre los adaptadores JPA de los siete puertos; los adaptadores en memoria solo se usan en tests de casos de uso. [`RoboLeagueApplication`](roboleague-api/src/main/java/com/roboleague/RoboLeagueApplication.java) inicia la aplicación. El ensamblado manual en `Main` corresponde a la Entrega 1 y ya no describe la ejecución actual.
  - **Transacciones externas al núcleo**: [`TransactionalUseCases`](roboleague-api/src/main/java/com/roboleague/api/config/TransactionalUseCases.java) aplica proxies de clase a beans del paquete exacto `com.roboleague.usecase`. El rollback cubre todas las escrituras, porque todos los agregados están en Postgres.
  - **Higiene de Importaciones**: Cero uso e importación de `System.out`, `Scanner`, `Gson` o `Unirest` en todo el paquete de dominio.

- **Pros**:
  - **Dominio 100% Puro y Portable**: El código de negocio puede ser reutilizado sin modificaciones en una aplicación Spring Boot, una API Quarkus, una CLI, o una arquitectura serverless.
  - **Pruebas del núcleo sin frameworks**: Los tests de dominio y casos de uso usan objetos Java y adaptadores en memoria, sin mocks (Mockito se quitó en 2.31). Las garantías de JPA, concurrencia, transacciones y HTTP requieren sus pruebas de integración.
  - **Facilidad de Mantenimiento**: El grafo completo de dependencias de la aplicación se comprende de un solo vistazo inspeccionando el Composition Root.

- **Contras**:
  - **Configuración de ensamblado**: Aunque Spring gestiona los beans, se mantienen registros explícitos de casos de uso y selección de adaptadores. Debe haber un único bean por puerto.
  - **Restricciones de proxies**: La interceptación transaccional depende del paquete y de clases/métodos aptos para proxies. Cambiar esa estructura exige verificar el cableado.

---

### 2.8 Desafíos con reglamento versionado (`Challenge` + `Rulebook`)

- **Problema**:  
  En la Entrega 1 leímos “desafío” como la forma de puntuar de una edición: una `Edition` era una sola prueba con un `ScoringPolicy`. La Entrega 2 rompe esa lectura: la demo pide tres desafíos en el mismo evento (uno mixto), y el reglamento define por desafío las mejores N de M rondas (F1) y el tope de bonificaciones (F2), y el desafío mixto (F3) declara qué fuentes exige. Con un reglamento por edición no hay dónde poner eso.

- **Solución Implementada**:
  - **`Challenge`** es un agregado propio que referencia su edición por id (`Edition` no crece y no conoce a sus desafíos). Rondas y tablas de posiciones son por desafío y lo referencian directo: `RoundScope` incluye el `ChallengeId` (2.23) y `StandingsId` se deriva de desafío y categoría (2.26). Cuando se tomó esta decisión todavía se agrupaban por edición y categoría.
  - **`Rulebook`** reemplaza a `ScoringPolicy`: una versión (`RulebookVersion`), cómo se puntúa (`ScoringScheme`, con al menos una regla; ver 2.12) y cómo se clasifica (`RankingScheme`, ver 2.10), sin exponer el composite. Es una clase y no un record para no publicar la regla compuesta como componente.
  - **Un desafío nunca está sin reglamento**: se crea con `Challenge.draft(id, edición, nombre).publish(reglas, esquema)`, que publica la v1. El borrador (`Draft`) no puede puntuar; solo sabe publicar. Así se evita un `Optional` en `currentRulebook()` y la falla de "desafío sin reglamento". El borrador es solo el paso de creación: el desafío copia sus datos y no lo guarda.
  - **El desafío numera las versiones**: `publish(reglas, esquema)` crea la siguiente (`v1`, `v2`…) y nunca edita la anterior. `currentRulebook()` da la vigente para capturar; `rulebook(versión)` da una exacta para recalcular con la versión con que se puntuó (lo que necesita el hallazgo 2).
  - **Category** sigue siendo elegibilidad (edad, composición, robot), no un desafío.
  - **Ids**: `ChallengeId` nació tipado porque era nuevo; el id de edición era `String` y pasó a `EditionId` con el resto de las identidades tipadas (2.21).
  - **`Rulebook` es una entidad hija de `Challenge`**, identificada por su versión dentro del desafío; no es un value object comparable por valor.

- **Pros**:
  - Agregar un desafío es crear un `Challenge`, sin tocar `Edition` ni las reglas existentes.
  - Dentro de un desafío no puede haber dos “v1” distintas: la versión la asigna `Challenge` al publicar, no quien carga el reglamento.
  - F1, F2 y F3 tienen dónde vivir: el reglamento de cada desafío.

- **Contras**:
  - La captura tiene que saber de qué desafío es el turno: `ReceiveResultUseCase` lo toma de la ronda y rechaza un `challengeId` distinto (2.23). La apelación ya no recibe el desafío: el intento guarda con qué versión se evaluó (`RulebookReference`, 2.18) y la aceptación usa esa.
  - Crear un desafío exige sus reglas iniciales: no se puede dar de alta "vacío" y cargar el reglamento después.

---

### 2.9 Recálculo: reprocesar posiciones, no re-evaluar métricas

- **Problema**:  
  La consigna pide dos cosas que se parecen y no son iguales: *“los resultados deben poder recalcularse utilizando exactamente la versión de reglas correspondiente”* y, en la tabla, *“Recálculo: reprocesar posiciones después de una corrección”*. Si se las fusiona, el caso de uso de ranking parecería tener que volver a correr todas las `ScoreRule` sobre cada `RawMetrics` cada vez que cambia una apelación.

- **Qué se eligió**:  
  - **Evaluar con la versión del reglamento del intento** (actualizado en 2.18): el intento se abre con la versión vigente de su desafío y la guarda; cada revisión, también la de una apelación aceptada, se puntúa con esa versión aunque el desafío publique otra. El snapshot guarda la versión, las métricas y el desglose.  
  - **Recalcular la tabla** (`RecalculateStandingsUseCase`) lee el último snapshot de cada intento, arma el total con la selección de rondas del reglamento y vuelve a ordenar con `RankingScheme`. Cumple la fila de la consigna: reprocesa **posiciones** después de una corrección. Cada cálculo es una versión nueva.  
  - No se re-aplica un reglamento sobre las mismas métricas: `Attempt.recalculateWith`, que lo permitía con cualquier versión, se borró en 2.18.

- **Pros**:  
  - El recálculo es barato y determinista: la tabla sigue a la fotografía vigente, no reinterpreta la pista.  
  - La versión del reglamento queda fijada cuando nace el snapshot. La edición 2027 no reescribe la 2026.  
  - Encaja la corrección por apelación: primero se evalúa de nuevo (métricas revisadas + la versión del reglamento que el intento guardó al abrirse, 2.18), después se reconstruye el ranking.

- **Contras**:  
  - Si un desafío publica una versión nueva, los intentos ya abiertos siguen con la suya: es lo que pide la consigna, pero una corrección del reglamento no alcanza a intentos anteriores.

---

### 2.10 Esquema de clasificación en el reglamento (F1 + hallazgo 8)

- **Problema**:  
  F1 pide que el reglamento defina, por desafío, cuántas rondas cuentan (mejores N de M) y que la explicación diga cuáles se consideraron y cuáles se descartaron, sin tocar las reglas de puntaje. El hallazgo 8 marcó que la cadena de desempate se armaba en `Main`, fuera de la edición, y que la explicación del empate era siempre el mismo texto.

- **Solución Implementada** (paquete `evaluation.scheme`):
  - **`RankingScheme`** es la tercera parte del `Rulebook`, junto a la versión y las reglas: `rulebook.rankingScheme()`. Agrupa la selección de rondas y la cadena de desempate porque las dos responden a "cómo se clasifica en este desafío", y tiene comportamiento propio (`decide`, `compare`).
  - **Strategy `RoundSelection`**: `BestNOfM(n, m)` y `AllRounds`. Trabaja sobre puntajes ya calculados (`RoundScore`: ronda + snapshot vigente del intento), así que no toca ninguna `ScoreRule`, que es el criterio de aceptación de F1.
  - **`ChallengeScore`** es la explicación de F1: rondas consideradas, descartadas y la regla aplicada ("mejores 2 de 3 rondas"), con total, mejor tiempo, faltas y nota de jueces de las consideradas.
  - **`TieBreakCriterion`** con nombre de dominio (`HigherTotal`, `LowerTime`, `FewerPenalties`, `HigherJudgeScore`; desde 2.17, `LowerDeductions` reemplaza a `FewerPenalties`). `RankingScheme.decide` recorre la cadena en el orden declarado y devuelve un resultado `sealed`: `DecidedBy(criterio, orden)` o `Tied`. Que el desenlace sea un tipo propio y no un `if` sobre el tipo de criterio es lo que permite la regla #8.
  - **El total ordena primero**: la cadena siempre empieza con `HigherTotal` y no repite criterios; si no, `RankingScheme` la rechaza. Sin esa guarda, un reglamento con solo `lower-time` ordenaba ignorando el puntaje, y la consigna pide ordenar por puntaje y desempatar después.
  - **Ausencia sin relleno**: un equipo sin rondas no tiene tiempo, y una ronda sin panel de jueces no tiene nota (`OptionalDouble` vacío); quedan últimos en esos criterios en vez de usar `Double.MAX_VALUE` o 0 (regla #24).
  - **Por qué en `evaluation` y no en `ranking`**: el esquema es parte del reglamento, que vive en `evaluation`; `ranking` ya depende de `evaluation`, y al revés habría un ciclo de paquetes.

- **F1: clases agregadas**: `RankingScheme`, `RoundSelection`, `BestNOfM`, `AllRounds`, `RoundScore`, `ChallengeScore`, `TieBreakCriterion` y sus implementaciones (`HigherTotal`, `LowerTime`, `HigherJudgeScore` y `FewerPenalties`, reemplazada por `LowerDeductions` en 2.17), `TieBreakDecision`. El consumidor de F1, la tabla por desafío, llegó en 2.26: `StandingsTable`, `TeamAttempts`, `TeamRounds`, `RoundResult` y, en la API, `StandingsVersionDto.EntryDto` (`selectionRule`, `considered`, `discarded`).
- **F1: clases modificadas**: `Rulebook` (tercer componente `rankingScheme`), `Challenge` (`Draft.publish` y `publish` reciben reglas y esquema) y `DemoFixture`. La demo actual, con descartes y desempates visibles en Laberinto, es la de 2.30.
- **F1: refactors**: ninguno sobre las reglas de puntaje; es una pieza nueva que se enchufa en el reglamento.
- **F1: deuda que decidimos no resolver en este paso**:
  - ~~La tabla de posiciones todavía no usa el esquema.~~ Cerrada en 2.26: `StandingsTable` arma por equipo las rondas que el reglamento cuenta, ordena con `rulebook.rankingScheme()` y explica cada posición con `decide`. `TieBreakerChain` y `TeamScore` se eliminaron (regla #27).
  - ~~M no se valida contra las rondas programadas.~~ Resuelta: `RoundSelection.allowsRound(n)` (`BestNOfM` acepta hasta M) y `ScheduleRoundUseCase` rechaza con `BusinessRuleViolation` (422) la ronda M+1 antes de generar turnos; `BestNOfM.select` conserva su chequeo como red de seguridad.
  - ~~Una versión nueva del reglamento con una M menor que las rondas ya programadas se publicaba, y desde ahí todo recálculo de la tabla (también el de una apelación aceptada o una corrección) fallaba en `BestNOfM.select`.~~ Resuelta: `PublishRulebookUseCase` consulta `allowsRound` con cada ronda guardada del desafío y devuelve `Publication.Rejected` (422) con una línea por ronda que la versión nueva no podría contar.
  - ~~Elegir el intento que representa a un equipo en una ronda queda en quien arma los `RoundScore`.~~ Cerrada en 2.26: `TeamAttempts` toma, por ronda, el mejor intento cuyo puntaje cuenta (`countableScore()`); uno descalificado o que espera una fuente no da ronda.
- **Patrones no aplicados**: Decorator sobre `RoundSelection` (no hay variación que lo pida). Los criterios por nombre llegaron después, con la configuración por API (`RuleCatalog`, 2.13).

- **Pros**: agregar una forma de seleccionar rondas o un criterio de desempate es una clase nueva, sin tocar las existentes (OCP); el orden del desempate es dato del reglamento y viaja con su versión; cada posición explica qué criterio la decidió (desde 2.26).
- **Contras**: un criterio de desempate nuevo que mire otra métrica exige exponerla en `ChallengeScore`.

---

### 2.11 Reglas medidas y fuentes de resultado (demo + F3, lado del reglamento)

- **Problema**:  
  La demo pide diez tipos de reglas, una regla compuesta y un desafío mixto. F3 pide que un mismo turno reciba resultados de dos fuentes (mediciones automáticas y panel de jueces), que el puntaje quede pendiente hasta tener las dos y que la explicación detalle cada contribución por separado. El reglamento es quien sabe qué fuentes necesita un desafío y qué regla lee de cuál.

- **Solución Implementada**:
  - **`ResultSource`** (mediciones automáticas, panel de jueces) y **`ScoreRule.source()`**: cada regla declara de qué fuente lee. Las cinco reglas existentes devuelven una fuente fija; las nuevas la toman de la métrica que leen.
  - **`Metric(nombre, fuente)`**: una medición con nombre (precisión, víctimas rescatadas, salidas de línea…) que se lee de `RawMetrics.measurement(métrica)`. Si no fue capturada, falla nombrando la métrica en vez de devolver 0 (regla #24); es excepción y no resultado porque evaluar sin todas las fuentes no debería pasar: esperar las fuentes es el estado "esperando fuentes" del intento.
  - **Cuatro reglas nuevas**: `PrecisionRule`, `VictimsRule` (suma por rescatada y resta por abandonada; desde 2.17 la resta es `AbandonedVictimsRule`), `MilestoneBonusRule` y `CountedFaultRule` (faltas contadas con franquicia). "Salidas de línea" y "Colisiones" son dos instancias de `CountedFaultRule` con otra métrica, no dos clases (reglas #6 y #7). Con las cinco de la Entrega 1 eran nueve clases de regla, más la compuesta. En 2.17 se separaron dos más (`AllObjectivesBonusRule` y `AbandonedVictimsRule`): hoy `RuleCatalog.standard()` registra **doce tipos de regla** (seis base contando la compuesta, dos bonificaciones y cuatro deducciones; tabla en el README). El tope de F2 no es un tipo de regla sino un `BonusLimit` (2.12).
  - **Regla compuesta "Desempeño en pista"** = Tiempo + Objetivos, con `CompositeScoreRule`. Una regla compuesta exige que sus hijas lean de la misma fuente, para que su fuente esté definida.
  - **`Rulebook.requiredSources()`** y **`Rulebook.contributions(métricas)`**: las fuentes que exige el reglamento y el desglose separado por fuente (`SourceContribution`). El piso en cero y el tope de F2 quedan en el desglose total, no en una fuente. Como un reglamento mixto mezcla fuentes, las reglas se guardan en listas (hoy dentro del `ScoringScheme`, 2.12) en vez de envolverse en un `CompositeScoreRule`; combinar evaluaciones vive en un solo lugar (`RuleEvaluation.combining` y `RuleEvaluation.concat`).

- **F3 (lado del reglamento): clases agregadas**: `ResultSource`, `Metric`, `SourceContribution`, `CountedFaultRule`, `FaultTariff`, `PrecisionRule`, `VictimsRule`, `VictimTariff`, `MilestoneBonusRule`, `Milestone`.
- **F3 (lado del reglamento): clases modificadas**: `ScoreRule` (`source()` y `RuleEvaluation.combining`), las cinco reglas existentes (`source()`), `CompositeScoreRule` (fuente única), `Rulebook` (lista de reglas, `requiredSources`, `contributions`), `RawMetrics` (`measurement`), `EvaluationFeedback` (mediciones con nombre) y `DemoFixture`. Hoy "Desempeño en pista" puntúa Laberinto y Rescate es el desafío mixto (2.30).
- **F3 (lado del reglamento): refactors**: `Rulebook` deja de usar un composite interno; `CompositeScoreRule.evaluateBreakdown` desaparece.
- **F3 (lado del reglamento): deuda que decidimos no resolver en este paso**:
  - ~~Las mediciones se leen por nombre: un nombre mal escrito compila y recién falla al evaluar.~~ Cerrada en 2.16: el reglamento declara sus métricas y rechaza una regla que lee una no declarada.
  - ~~El intento todavía no espera sus fuentes ni guarda contribuciones; un ajuste de faltas pierde las mediciones del intento (issue #5).~~ Cerrada en 2.18: el intento queda `AWAITING_SOURCES` hasta recibir cada fuente que exige su reglamento, y `RawMetrics.withPenalties` cambia solo las faltas.
  - ~~`averageJudgeScore()` devuelve 0 sin jueces; un desafío que exige panel no debería evaluarse sin él.~~ Cerrada en 2.18 y 2.31: una regla de jueces declara la fuente `JUDGE_PANEL`, así que el intento espera al panel, y `JudgeScores` exige al menos una nota. El 0 de `EvaluationFeedback.averageJudgeScore()` solo lo ve un desafío sin reglas de jueces.
  - Una medición fuera de rango (precisión fuera de 0 a 1, conteo no entero o negativo, más víctimas rescatadas que las del desafío) se rechaza con `IllegalArgumentException` al evaluar. Desde 2.16 la captura se puede revisar antes con `Rulebook.check`, que devuelve el rechazo como resultado (regla #28); la excepción al evaluar queda para una invariante rota. ~~Falta que el caso de uso de captura lo llame.~~ Cerrada en 2.18: `Attempt.receive` llama a `Rulebook.check` con cada fuente y `ReceiveResultUseCase` devuelve el rechazo como 422.
  - `PenaltyRule` (lee `penaltiesCount`) y `CountedFaultRule` sin franquicia calculan lo mismo con el conteo de otra parte. Se unifican cuando las faltas sean una métrica declarada más.
  - `ScoreRule.source()` devuelve una sola fuente. El tope de bonificaciones (F2) agrupa bonificaciones de fuentes distintas, así que se aplica en el reglamento sobre el conjunto de bonificaciones y no como una regla más con fuente propia (ver 2.12).
- **Patrones no aplicados**: mapa fuente → reglas en el constructor del reglamento (duplica lo que cada regla ya sabe); fuente en cada `ScoreItem` (el piso y el tope de F2 no tienen fuente).

---

### 2.12 Tope global de bonificaciones (F2)

- **Problema**:  
  F2 pide que la suma de todas las bonificaciones de un desafío no supere un máximo configurable, sin importar cuántas se hayan obtenido. El tope va sobre el conjunto y no sobre cada una, la explicación tiene que mostrar lo obtenido, el tope y el recorte, y agregarlo no puede exigir reescribir las reglas de bonificación.

- **Solución Implementada**:
  - **`ScoringScheme(reglas, bonificaciones, límite)`** es cómo puntúa un reglamento: `Rulebook(versión, ScoringScheme, RankingScheme)`. Separa las bonificaciones del resto porque el tope necesita saber cuáles son. Las bonificaciones son `ScoreRule` comunes (por ejemplo `MilestoneBonusRule`); ninguna cambió.
  - **Strategy `BonusLimit`**: `Unlimited` (sin tope) y `CappedAt(máximo)`. El límite recibe la suma de las bonificaciones y devuelve su ajuste. `CappedAt` agrega siempre el ítem "Tope de bonificaciones" con lo obtenido, el tope y el recorte (0 si no hace falta), así la explicación muestra el tope aunque no recorte.
  - **Por qué no es un decorator que envuelve las reglas de bonificación como una regla más**: las bonificaciones pueden venir de fuentes distintas (víctimas del panel, hitos de los sensores) y una `ScoreRule` declara una sola fuente (2.11). El límite se aplica en el esquema, sobre el resultado del grupo, que es el mismo efecto sin inventarle una fuente.
  - El recorte, como el piso en cero, queda en el desglose total y no en la contribución de ninguna fuente.

- **F2: clases agregadas**: `ScoringScheme`, `BonusLimit`, `Unlimited`, `CappedAt`.
- **F2: clases modificadas**: `Rulebook` (recibe un `ScoringScheme` en lugar de una lista de reglas y le delega fuentes y contribuciones), `ScoreRule.RuleEvaluation` (`concat`), `Challenge` (`Draft.publish` y `publish` reciben el `ScoringScheme`), `DemoFixture` y los tests que arman reglamentos (`ScoringScheme.withoutBonuses(reglas)`).
- **F2: refactors**: ninguno sobre las reglas de bonificación.
- **F2: deuda que decidimos no resolver**:
  - ~~Nada impide poner una regla que resta en la lista de bonificaciones.~~ Cerrada en 2.17: cada regla es base, bonificación o deducción por tipo, y el bono por completar objetivos, que escapaba al tope, es una bonificación más.
  - ~~La demo no muestra el tope.~~ Cerrada en 2.15: los tres reglamentos de la demo tienen tope (40 / 30 / 30) y el desglose de los intentos de Laberinto muestra el recorte (demo actual en 2.30).
- **Patrones no aplicados**: Decorator sobre `ScoreRule` (ver arriba); tope por regla (la consigna pide sobre el conjunto).

---

### 2.13 Reglamento configurable: definiciones y catálogo de reglas

- **Problema**:  
  La consigna pide configurar desafíos (métricas, reglas, penalizaciones, bonificaciones) desde la aplicación, y el desafío tiene que guardarse en Postgres. En los dos bordes llega un texto (`"type": "time"`) que tiene que convertirse en una clase, sin un `switch` por tipo en el dominio (regla #8) y con esa decisión inevitable en un solo lugar (regla #9).

- **Solución Implementada**:
  - **Definiciones** (paquete `evaluation.definition`): `RuleDefinition(tipo, nombre, argumentos)`, `StrategyDefinition`, `RulebookDefinition` y `Parameters` describen un reglamento con datos simples. Un parámetro faltante falla nombrándolo, nunca vale 0 (regla #24).
  - **Cada pieza se describe y se reconstruye en su propia clase** (regla #3): las reglas, `BestNOfM`, `AllRounds`, `CappedAt` y `Unlimited` tienen `TYPE`, `definition()` y `from(definición)`; los criterios tienen un `code()` estable (`higher-total`…), distinto del nombre que se muestra.
  - **`RuleCatalog`** es el único lugar donde un tipo se convierte en clase: un mapa de tipo → constructor, no un `switch`. Agregar un tipo de regla es la clase nueva (con `TYPE`, `from` y `definition()`) y una entrada en `RuleCatalog.standard()`, sin tocar las existentes (receta en el README). Lo usan la API y la persistencia, por eso vive en el dominio y no duplicado en cada borde (regla #27). La regla compuesta reconstruye a sus hijas con el mismo catálogo.
  - **Falla esperable como resultado** (regla #28): `RuleCatalog.assemble` devuelve `Assembled(scoring, ranking)` o `Rejected(problemas)`, con todos los problemas juntos y nombrando la regla. Es el punto donde la excepción de una invariante (parámetro inválido en un constructor) se vuelve un resultado para lo que llegó de afuera. Los casos de uso devuelven `Publication.Published` o `Publication.Rejected`; el controller lo traduce a 201 o 422.

- **Alternativas descartadas**: anotaciones de Jackson (`@JsonTypeInfo`) sobre las reglas (meten un tercero en el dominio, regla #11); una factory en la API y otra en la persistencia (la misma decisión dos veces); guardar las reglas serializando las clases (el esquema de la tabla quedaría atado a los campos privados).
- **Patrones no aplicados**: Visitor para describir reglas (cada regla ya sabe describirse); Builder para armar reglamentos (los records alcanzan).
- **Deuda**:
  - ~~Una regla que resta puede declararse como bonificación (2.12) y los nombres de métrica son texto libre.~~ Cerradas en 2.17 y 2.16.
  - `RuleCatalog.standard()` conoce las doce implementaciones desde el dominio. Son conceptos del dominio, no detalles técnicos, pero la lista podría armarse en el composition root (`UseCaseConfig`) para dejar la decisión más cerca del borde (regla #9).
  - Ciclo de paquetes `evaluation` ↔ `evaluation.definition` (por `Metric`), que se suma al de `evaluation` ↔ `evaluation.rules`. Se corta moviendo `Metric` y `ResultSource` a un paquete hoja.
  - La traducción JSON ↔ definición está escrita dos veces, en `RulebookBody` (API) y en `ChallengeMapper` (persistencia), porque cada borde fija su propia forma de datos; si las formas coinciden siempre, se puede compartir.
  - El catálogo convierte cualquier `IllegalArgumentException` de un constructor en un problema 422: también lo haría un bug. Validar antes de construir las claves que cada tipo exige dejaría el `catch` solo para invariantes de rango.

### 2.14 Persistencia del desafío

- **Solución Implementada**: tabla `challenges` (migración `V2`) con id, edición, nombre y una columna JSONB con todas las versiones del reglamento como definiciones. `ChallengeMapper` (capa anticorrupción, como `AppealMapper`) guarda `rulebook.definition()` y, al leer, reconstruye cada versión con `RuleCatalog` y el agregado con `Challenge.restore`, que exige versiones consecutivas desde 1. Si una versión guardada no se puede reconstruir es un dato corrupto: `CorruptDataException` (ver la deuda de dato corrupto, abajo).
- **Por qué una columna JSON y no tablas por regla**: un reglamento es un árbol (reglas compuestas, bonificaciones, estrategias) que se lee y se escribe entero y nunca se consulta por partes. Las versiones viejas no cambian. La forma del JSON la fijan records propios de infraestructura, no las clases del dominio.
- **Sin clave foránea a `editions`**: son agregados distintos y se referencian por id.
- **Deuda (resuelta en 2.27)**: `Edition` y Team estaban en memoria y, sin el perfil `demo`, un desafío guardado podía quedar apuntando a una edición perdida al reiniciar.
- ~~**Deuda: concurrencia.**~~ Resuelta: todos los adaptadores JPA guardan con `saveAndFlush` y traducen el rechazo de Postgres (versión vieja o clave repetida) a `ConcurrentModificationConflict` (409) con la causa encadenada. `challenges` y `appeals` tienen `@Version` desde la migración `V9`; sus adaptadores recuerdan con qué versión cargaron cada instancia, como `JpaAttemptRepository`, así que dos publicaciones o dos revisiones simultáneas ya no se pisan.
- ~~**Deuda: dato corrupto.**~~ Resuelta: los mappers envuelven toda reconstrucción (`Enum.valueOf`, eventos desconocidos, reglamentos que el catálogo rechaza) en `CorruptDataException`, que la API responde como 500 genérico y registra en el log.

### 2.15 Casos de uso y API de configuración; demo con tres desafíos

- **Casos de uso**: `CreateEditionUseCase`, `AddChallengeUseCase`, `PublishRulebookUseCase`, `GetChallengeUseCase`. Id repetido → `ConflictException` (409); edición o desafío inexistente → `NotFoundException` (404; antes era `IllegalArgumentException`/400); reglamento rechazado → 422 con los problemas. La API valida los datos obligatorios de la edición antes de construir el dominio (un nulo llegaba como 500); `Edition` rechaza dos categorías con el mismo id, y `EditionHeader`, `Category` y `Challenge.Draft` rechazan ids y nombres en blanco.
- **API**: `POST /editions`, `POST /editions/{id}/challenges`, `POST /challenges/{id}/rulebook/versions`, `GET /challenges/{id}`. El JSON de un reglamento es el mismo al mandarlo y al leerlo (`RulebookBody`). Los DTOs son vistas planas del JSON, como `AppealDto`; la regla de tres parámetros se aplica al modelo.
- **Demo**: `DemoFixture` crea la edición y los tres desafíos con los casos de uso (`DemoRulebooks`), publica la v2 del Seguidor y corre el flujo de apelación sobre Laberinto. En ese momento las rondas por desafío, la captura del desafío mixto y la tabla con N de M todavía no existían; llegaron en 2.23, 2.18 y 2.26. La demo actual (cuatro rondas en Laberinto, descartes y desempates visibles, Rescate con dos fuentes, ids y horas fijos) es la de 2.30.
- **Clases agregadas**: definiciones, `RuleCatalog`, `RulebookAssembly`, `Publication`, los cuatro casos de uso con sus comandos, `JpaChallengeRepository`, `ChallengeJpaEntity`, `ChallengeMapper`, `ChallengeController`, `EditionController`, `DemoRulebooks`.
- **Clases modificadas**: todas las reglas, estrategias y criterios (`definition()`/`from`/`code()`), `ScoringScheme`, `RankingScheme`, `Rulebook` (`definition()`), `Challenge` (`restore`), `UseCaseConfig`, `InMemoryRepositoryConfig` (sin bean en memoria de desafíos; la clase se eliminó en 2.27), `DemoConfig`, `DemoFixture`.

---

### 2.16 Métricas declaradas en el reglamento (`MetricDefinition`, hallazgo 8)

- **Problema**:
  La consigna pide configurar "métricas" en cada desafío y el hallazgo 8 marcó que el reglamento no las declaraba: las reglas leían mediciones por nombre libre, un nombre mal escrito recién fallaba al puntuar y nada revisaba lo capturado (una precisión de 1.4 o 2.5 colisiones reventaban en medio del cálculo).

- **Solución Implementada**:
  - **`MetricDefinition(Metric, MeasurementUnit, ValueRange)`**: qué se mide, en qué unidad y qué valores puede tomar. `MeasurementUnit` es un enum cuya constante `COUNT` redefine `accepts` para exigir enteros (polimorfismo de enum, sin `if` por unidad). `ValueRange` incluye los extremos; sin `max` queda abierto hacia arriba.
  - **`MetricSheet`** agrupa las métricas de un reglamento y rechaza nombres repetidos. Vive en el `ScoringScheme`, que pasa a ser `(MetricSheet, ScoreRules, BonusLimit)`: las dos listas de reglas se agrupan en `ScoreRules` para respetar los tres parámetros (regla #2). El reglamento queda como lo cuenta el negocio: qué se mide, cómo se puntúa, cómo se clasifica.
  - **Cada regla dice qué lee** (`ScoreRule.metrics()`, la compuesta devuelve la unión de sus hijas) y el `ScoringScheme` rechaza una regla que lee una métrica no declarada o declarada con otra fuente. Por la API es un 422 que nombra la regla y la métrica, junto con el resto de los problemas del catálogo.
  - **Revisar una captura**: `Rulebook.check(fuente, mediciones)` devuelve `MeasurementCheck.Accepted` o `Rejected(problemas)` (regla #28) con todo junto: métricas faltantes, no declaradas para esa fuente, no enteras o fuera de rango. Revisa una fuente por vez, que es como llegan en el desafío mixto (F3).
  - **Definición, API y Postgres**: `RulebookDefinition(metrics, scoring, ranking)` con `MetricDeclaration` (el rango viaja como números `min`/`max`, así un rango inválido es un problema 422 del catálogo y no un 400). El JSON del reglamento suma `"metrics"` arriba de todo; se guarda en la misma columna JSONB, sin migración.

- **Clases agregadas**: `MetricDefinition`, `MeasurementUnit`, `ValueRange`, `MetricSheet`, `ScoreRules`, `MeasurementCheck`, `MetricDeclaration`.
- **Clases modificadas**: `ScoreRule` y las diez reglas que existían entonces (`metrics()`), `ScoringScheme`, `Rulebook` (`check`, `definition`), `RulebookDefinition`, `RuleCatalog`, `RulebookBody`, `RulebookDto`, `ChallengeJpaEntity`, `ChallengeMapper`, `DemoRulebooks` y los tests que arman reglamentos con reglas medidas.
- **Refactors**: `ScoringScheme` agrupa sus listas en `ScoreRules`; el resto de las reglas no cambia cómo puntúa.
- **Alternativas descartadas**:
  - Que las reglas referencien la métrica solo por nombre y tomen fuente, unidad y rango de la declaración: más limpio, pero cambia la definición de las cuatro reglas medidas y el JSON de cada regla. Se dejó la referencia `{"name", "source"}` y se valida que coincida.
  - Validar la captura dentro del caso de uso de captura: repetiría en application una regla del reglamento. El reglamento ofrece `check` y, desde 2.18, el intento lo llama al recibir cada fuente.
  - Un cuarto parámetro en `ScoringScheme` o en `Rulebook`: rompe la regla #2.
- **Patrones no aplicados**: Specification para las métricas (no hay combinaciones `and`/`or`: cada métrica se revisa sola contra su definición).
- **Deuda que decidimos no resolver en este paso**:
  - Tiempo, objetivos, faltas, consumo y notas de jueces siguen siendo campos fijos de `TrackPerformance`/`EvaluationFeedback` y no se declaran. Pasarlos a métricas declaradas toca el intento y la persistencia de apelaciones (frentes 2 y 4); con eso se podría unificar `PenaltyRule` con `CountedFaultRule` (2.11).
  - Los reglamentos guardados antes de este cambio no tienen `metrics`: si alguna regla lee una métrica con nombre ya no se reconstruyen (`CorruptDataException`, 500). Solo había datos de la demo, que se recargan en cada arranque.
  - Se permiten métricas declaradas que ninguna regla lee; la captura igual las exige.
  - El rango de una métrica no se cruza con los parámetros de la regla (por ejemplo, que el máximo de víctimas rescatadas coincida con `totalVictims`).

---

### 2.17 Reglas base, bonificaciones y deducciones; desempate por descuento

- **Problema**:
  F2 pide que el tope se aplique sobre "la suma de todas las bonificaciones". El reglamento tenía dos listas sin tipo (reglas y bonificaciones), así que nada impedía poner una penalización entre las bonificaciones (lo obtenido daba negativo y el tope no recortaba), y la regla de objetivos traía adentro un bono por completarlos todos que el tope no veía. `VictimsRule` sumaba y restaba en la misma regla. Además, el criterio `fewer-penalties` comparaba solo el campo fijo de faltas: en el Seguidor de línea, cuyas faltas son salidas de línea medidas, empataba siempre. Y un parámetro mal escrito se ignoraba en silencio.

- **Solución Implementada**:
  - **Tres secciones tipadas**: `BaseRule`, `BonusRule` y `DeductionRule` extienden `ScoreRule` sin métodos nuevos; el contrato (una base o una bonificación nunca resta, una deducción nunca suma) está en el Javadoc y lo verifica `ScoreRuleSectionsTest`. `ScoreRules(List<BaseRule>, List<BonusRule>, List<DeductionRule>)`: el compilador impide una penalización entre las bonificaciones, sin preguntar el tipo en tiempo de ejecución (regla #8). La compuesta solo admite reglas base.
  - **El catálogo por sección**: `RuleCatalog` registra cada tipo en su `RuleSection` (base, bonificación o deducción); registrar un tipo solo compila si su regla es de esa sección. Un tipo en la lista equivocada es un problema 422 que dice a qué sección pertenece (`"type 'penalty' is a deduction, not a bonus"`).
  - **Se separan las reglas que mezclaban**: el bono por completar todos los objetivos pasa a ser la bonificación `all-objectives` (`AllObjectivesBonusRule`), y `ObjectivesRule` (ex `ObjectiveBonusRule`) queda con los puntos por objetivo. Las víctimas abandonadas son la deducción `abandoned-victims` (`AbandonedVictimsRule`), que es la que conoce el total de víctimas. No se reescribe ninguna regla de bonificación: se saca un bono que estaba escondido en una regla base, así el tope de F2 cubre de verdad todas las bonificaciones.
  - **El desglose por sección**: `ScoreBreakdown(base, bonificaciones con su tope, deducciones)`. Los ítems, las notas y el total se derivan (el total ya no puede contradecir a los ítems) y el piso en cero sigue apareciendo como ítem. `deducted()` dice cuántos puntos quitaron las deducciones.
  - **Desempate por descuento**: `LowerDeductions` (`lower-deductions`) compara los puntos descontados en las rondas consideradas, sea cual sea la deducción (faltas, salidas de línea, colisiones, consumo). Un equipo sin rondas no tiene descuento y queda último (regla #24). Reemplaza a `FewerPenalties`.
  - **Nada desconocido se ignora**: una regla, una estrategia o un rango con un parámetro, una métrica o reglas anidadas que no usa se rechaza (422). Para saber qué acepta cada pieza se la compara con su propia `definition()`, así la lista de nombres válidos no se repite (regla #27).
  - **Validaciones chicas**: `basePoints` de la regla de tiempo no puede ser negativo, y `ObjectivesRule` formatea con `Locale.US` como las demás.

- **Impacto en la demo**: el bono de "Todos los objetivos" entra al tope de Laberinto. TitanTeam pasa de 222,5 a 197,5 (el tope recorta 35 en vez de 10) y, después de la apelación, de 282,5 a 257,5; CyberTeam sigue en 235. La apelación sigue dando vuelta el orden. El descuento de TitanTeam baja de 65 a 5 puntos con la apelación.
- **Clases agregadas**: `BaseRule`, `BonusRule`, `DeductionRule`, `RuleSection`, `AllObjectivesBonusRule`, `AbandonedVictimsRule`, `LowerDeductions`, `RulebookDefinition.Bonuses`.
- **Clases modificadas**: las doce reglas (sección), `ObjectivesRule`, `VictimsRule`, `VictimTariff`, `CompositeScoreRule`, `TimeTargets`, `ScoreRule.RuleEvaluation` (`combining` con comodín), `ScoreRules`, `ScoringScheme`, `ScoreBreakdown`, `Rulebook`, `RuleCatalog`, `RulebookDefinition`, `Parameters` y `RuleArguments` (`unknownTo`), `RoundScore` y `ChallengeScore` (`deducted`), `RulebookBody`, `RulebookDto`, `ChallengeJpaEntity`, `ChallengeMapper`, `DemoRulebooks`.
- **Clases eliminadas**: `ObjectiveRuleConfig`, `FewerPenalties`.
- **Refactors**: `ObjectiveBonusRule` pasa a llamarse `ObjectivesRule`; `ScoreBreakdown` guarda sus secciones y deriva el total.
- **Alternativas descartadas**:
  - Un `kind()` en cada regla y validar la lista al publicar: es preguntar el tipo (regla #8), deja armar en código esquemas inválidos que recién fallan al construirse, y no ahorra lo caro (partir objetivos y víctimas, el desglose por sección).
  - Una sola lista en el JSON que el catálogo reparte por tipo: el JSON deja de mostrar qué se bonifica y qué se descuenta.
  - Marcar la sección en cada `ScoreItem`: cuatro parámetros (regla #2) y filtrar por un enum.
  - Que el ranking vuelva a evaluar las deducciones con el reglamento: reinterpreta la pista al rankear (2.9).
  - Una migración que reescriba los reglamentos guardados: meter el bono de objetivos dentro del tope cambiaría cómo puntúa una versión ya publicada.
- **Patrones no aplicados**: compuesta por sección (alcanza con la base; se podría registrar el mismo tipo en cada sección sobre un grupo genérico); `ScoreRule` sellada (cerraría el conjunto de reglas, contra OCP); Visitor para clasificar.
- **Deuda que decidimos no resolver en este paso**:
  - Los reglamentos guardados antes de este cambio (una penalización en `rules`, `allCompletedBonus`, `fewer-penalties`) no se reconstruyen. Solo hay datos de la demo, que se recargan en cada arranque.
  - El bono por segundo bajo el objetivo de la regla de tiempo sigue siendo parte de la base y no entra al tope.
  - `all-objectives` es un `milestone` sobre los objetivos y `penalty` un `counted-fault` sobre las faltas: se unifican cuando esos campos fijos sean métricas declaradas (2.16).
  - ~~`TieBreakerChain` y `TeamScore` siguen contando faltas hasta que la tabla use `RankingScheme` (2.10).~~ Cerrada en 2.26.
  - Una nota de jueces negativa rompería el contrato de la base: `EvaluationFeedback` no valida su signo.
  - ~~La API ignora las claves JSON desconocidas.~~ Cerrada en 2.19 (issue #14): un campo desconocido es 400.

---

### 2.18 Intento y captura: identidad del turno, estados, versión del reglamento y dos fuentes (F3, hallazgos 2, 3 y 4)

- **Problema**:
  El intento recibía resultados en vez de producirlos. Su id lo elegía quien capturaba y el repositorio hacía `put`, así que capturar dos veces borraba el original (hallazgo 4); no se validaba turno, equipo ni juez. El desglose entraba armado desde el caso de uso, en cualquier estado: un intento descalificado y sin apelación quedó en 9999 (hallazgo 3). El estado era un enum que cualquier método pisaba, así que rechazar una de dos apelaciones lo liberaba, y un equipo con su único intento descalificado entraba a la tabla con 185. El intento no sabía con qué reglamento se puntuó: una apelación aceptada lo reevaluaba con el desafío que pasara quien llamaba (hallazgo 2). Para F3, las mediciones y el panel de jueces tienen que llegar por separado al mismo turno y el puntaje tiene que quedar pendiente, y visible como tal, hasta que estén las dos. Además, un ajuste de faltas perdía las mediciones con nombre y el consumo (issue #5).

- **Solución Implementada**:
  - **Identidad desde el turno**: `AttemptId(slot, número)` la deriva el dominio (`slot-7-1`); `AttemptIdentity(id, ronda, equipo)`. El mismo turno llega al mismo intento y un segundo resultado de la misma fuente se rechaza; las correcciones van por un ajuste de faltas o una apelación, con autor y motivo.
  - **Cada fuente por separado (F3)**: lo que manda una fuente es un `SourceReport` sellado, `Measurements` (tiempo, objetivos, faltas, consumo y mediciones de sensores) o `JudgeScores` (nota de cada juez y mediciones del panel), y `SourceDelivery` le suma el juez que lo cargó. `Attempt.receive(entrega, reglamento)` revisa las mediciones con `Rulebook.check` y devuelve el `MeasurementCheck` (regla #28): si no cumplen, no cambia nada. Mientras falte una fuente que el reglamento exige el intento queda `AWAITING_SOURCES`, sin puntaje; con la última se puntúa con todas, en cualquier orden. Cada fuente suma lo suyo a las métricas (`addTo`) sin preguntar de qué tipo es (regla #8).
  - **El intento se puntúa solo**: ningún método público recibe un `ScoreBreakdown`. Una apelación aceptada trae métricas corregidas (`AppealRevision`) y el intento las puntúa; quién cambió y por qué viajan en `AuditNote`.
  - **Versión fijada (hallazgo 2)**: `RulebookReference(desafío, versión)` se guarda al abrir el intento y `EvaluationSnapshot` guarda la versión de cada revisión. Puntuar con otra versión falla. `ResolveAppealUseCase.acceptAppeal` ya no recibe el desafío: carga la versión del intento. `recalculateWith`, que puntuaba con cualquier reglamento, se borró.
  - **Estados (State, como `Appeal`)**: `AttemptState` con una clase por forma de comportarse: `WaitingAttemptState` (programado o esperando fuentes: solo toma resultados), `SettledAttemptState` (evaluado o ajustado: toma apelaciones, ajustes de faltas y la descalificación), `UnderAppealAttemptState` (cuenta las apelaciones abiertas y recuerda si vuelve a evaluado o ajustado) y `DisqualifiedAttemptState` (no toma nada). Lo que un estado no acepta lo rechaza un método por defecto que nombra el estado (409 en la API).
  - **Puntaje computable**: `countableScore()` es el desglose que cuenta, vacío antes de puntuar o si está descalificado (regla #24). La tabla solo usa intentos que cuentan (`TeamAttempts`, 2.26; antes `TeamScore`); un equipo sin ninguno no suma.
  - **Validación contra la ronda**: `ScheduleRoundUseCase` guarda la ronda completa en `RoundRepository` (extendido en 2.23); `Round.slot(id)` y `Slot.isJudgedBy(juez)`. `ReceiveResultUseCase` exige que el turno exista (404), que el desafío coincida con la ronda (409) y que el juez esté asignado (422), toma el equipo del slot y abre el intento con la versión vigente del desafío.
  - **API**: `PUT /attempts/{id}/measurements`, `PUT /attempts/{id}/judge-scores` y `GET /attempts/{id}/breakdown`, que muestra lo pendiente y lo que aportó cada fuente (README, "Cargar los resultados de un intento").
  - **Persistencia**: migración V3 con el turno, la versión y la etapa en columnas, y las entregas, revisiones y eventos en JSONB. `Attempt.restore(identidad, referencia, AttemptProgress)` reconstruye sin repetir transiciones, como `Appeal.restore`; `AttemptStage` es el estado como dato y el único lugar que elige un estado por su nombre (regla #9). `AttemptEvent` pasa a ser sellado para que el mapper guarde cada tipo.
  - **Issue #5**: `RawMetrics.withPenalties` cambia solo las faltas.

- **Impacto en la demo**: Laberinto no cambia (TitanTeam 197,5 → 257,5 con la apelación, CyberTeam 235). En Rescate, CyberTeam recibe las mediciones y el panel y suma 147,5; TitanTeam tiene las mediciones y espera el panel.
- **F3: clases agregadas**: `AttemptId`, `RulebookReference`, `AppealRevision`, `AuditNote`, `SourceReport`, `Measurements`, `JudgeScores`, `SourceDelivery`, `SourceReceivedEvent`, `AttemptState`, `WaitingAttemptState`, `SettledAttemptState`, `UnderAppealAttemptState`, `DisqualifiedAttemptState`, `AttemptStage`, `AttemptProgress`, `AuditTrail`, `RoundRepository`, `InMemoryRoundRepository`, `ReceiveResultUseCase`, `ReceiveResultCommand`, `Reception`, `GetAttemptBreakdownUseCase`, `AttemptBreakdown`, `AttemptController`, `AttemptDto`, `BreakdownDto`, `AttemptJpaEntity`, `AttemptMapper`, `JpaAttemptRepository`, `SpringDataAttempts`, `MetricsJson`, y los cuerpos `MeasurementsBody` y `JudgeScoresBody`.
- **F3: clases modificadas**: `Attempt`, `AttemptIdentity`, `AttemptRepository`, `InMemoryAttemptRepository`, `RawMetrics`, `EvaluationSnapshot`, `AttemptScoreSnapshot`, `ResultRegisteredEvent`, `ScoreAdjustedEvent`, `AttemptEvent`, `TeamScore`, `Round`, `Slot`, `ScheduleRoundUseCase`, `FileAppealUseCase`, `ResolveAppealUseCase`, `PublishOfficialRankingUseCase`, `AppealJpaEntity`, `AppealMapper`, `UseCaseConfig`, `InMemoryRepositoryConfig`, `DemoFixture`, `DemoConfig`. (`TeamScore` y `PublishOfficialRankingUseCase` se eliminaron después, en 2.26; `InMemoryRepositoryConfig`, en 2.27.)
- **F3: clases eliminadas**: `SlotReference`, `CaptureAttemptResultUseCase`, `CaptureAttemptResultCommand`, y el método `Attempt.recalculateWith`.
- **F3: refactors**: el estado del intento pasa de un enum que cualquier método pisaba a clases `AttemptState` (State); la captura pasa de un caso de uso que armaba el desglose a `ReceiveResultUseCase`, y el intento se puntúa solo (ningún método público recibe un `ScoreBreakdown`); el id del intento se deriva del turno en vez de elegirlo quien captura; `RawMetrics.withPenalties` cambia solo las faltas (issue #5).
- **Alternativas descartadas**:
  - Dos casos de uso, `ReceiveMeasurements` y `ReceiveJudgeScores`, como decía la hoja de ruta: harían lo mismo con un reporte distinto (regla #27). Quedan los dos endpoints sobre un caso de uso.
  - Un método por fuente en el intento (`receiveMeasurements`, `receiveJudgeScores`): agregar una fuente obligaría a abrir `Attempt`. La fuente es una pieza nueva que implementa `SourceReport`.
  - Crear los intentos al programar la ronda: depende de cómo modele la ronda el frente 3. El primer resultado abre el intento, y el estado programado queda para cuando la ronda los cree.
  - Devolver como resultado la misma fuente dos veces o un cambio que el estado no acepta: es una transición no permitida, como en `Appeal`, y la convención de la API la traduce a 409. Los problemas de las mediciones y el juez no asignado sí son resultados (422).
  - Una clase por estado para evaluado y ajustado: aceptan lo mismo y solo cambia cómo se muestran (regla #7). Lo mismo para programado y esperando fuentes.
  - Guardar en el intento las fuentes que exige su reglamento: se deducen de su versión, que el intento ya guarda.
  - Reconstruir el desglose al leer de la base, con el reglamento: el snapshot guardado es el registro de auditoría y no tiene que depender del catálogo.
- **Patrones no aplicados**: event sourcing (reconstruir el intento desde sus eventos; se guardan revisiones y eventos tal cual); Visitor para las fuentes (alcanza con `addTo` polimórfico).
- **Dos fuentes al mismo tiempo**: en F3 las mediciones y el panel pueden llegar casi juntos. Postgres guarda el intento con bloqueo optimista (`@Version`, migración `V4`): `JpaAttemptRepository` recuerda con qué versión cargó cada intento y la manda al guardar, así que si otro pedido lo guardó en el medio el segundo se rechaza con 409 y se reintenta, en lugar de pisar la fuente que ya llegó. La versión es un detalle del adaptador: el agregado no la conoce.
- **F3: deuda que decidimos no resolver en este paso**:
  - `challengeId` sigue en el cuerpo de captura por compatibilidad; desde 2.23
    se valida contra la ronda.
  - RoundRepository fue extendido en 2.23; su persistencia llegó en 2.27. La
    exclusión de programaciones concurrentes sigue pendiente.
  - No se valida el horario del turno, y del panel de jueces solo se valida al juez que carga, no a cada juez que puntúa. Del estado del turno solo se rechaza uno cancelado (2.29): un turno `SCHEDULED` acepta resultados.
  - Descalificar solo se acepta sobre un intento puntuado, como el diagrama de estados.
  - ~~La tabla sigue armándose con `TeamScore` y `TieBreakerChain`.~~ Cerrada en 2.26: cada `RoundScore` sale de `countableScore()` y su snapshot, y ordena `RankingScheme`.
  - ~~Faltan las rutas de apelación.~~ Cerradas en 2.26: `POST /attempts/{id}/appeals`, revisión, aceptación y rechazo.
  - La dependencia estática de reloj/UUID se resolvió en la sección 2.20; los
    ids de referencias se tiparon en la sección 2.21.

---

### 2.19 Plataforma: transacciones, JSON estricto y demo con fechas fijas

- **Una transacción por caso de uso (issue #13)**: un caso de uso que guarda dos agregados (aceptar una apelación guarda la apelación y el intento) podía dejar uno guardado y el otro no. `TransactionalUseCases` envuelve cada bean del paquete de casos de uso en un proxy transaccional desde el composition root, como un decorador: `roboleague-application` sigue sin Spring (regla #11) y ningún caso de uso cambió. Un guardado que Postgres rechaza por versión vieja o id repetido es `ConcurrentModificationConflict` (409); cualquier otra violación de integridad es un 500, no un conflicto.
  - *Alternativas descartadas*: `@Transactional` en los casos de uso (mete Spring en la aplicación); un puerto `Transactions` inyectado en cada caso de uso (cambia constructores de varios frentes para el mismo efecto); transacciones en los controllers (la demo, que llama a los casos de uso directo, quedaría afuera).
- **JSON estricto (issue #14)**: un campo desconocido es 400 con el nombre del campo y los esperados, en lugar de ignorarse (`"penalties"` en vez de `"deductions"` perdía la penalización). El reglamento acepta y descarta `version` y `requiredSources`, que trae cuando se lo lee con `GET`, para poder mandarlo de vuelta tal cual.
- **Demo con fechas fijas**: la edición es del 10 al 12/11/2026 y las rondas arrancan desde las 9:00 del primer día, una por hora. Con esto solo no alcanzaba: ids y horas de operación seguían saliendo de UUID y del reloj del sistema hasta 2.30, que les da valores fijos en el perfil demo.
- **Actualización**: reloj e ids se inyectan como puertos (2.20). La spec 03
  resolvió el calendario de elegibilidad usando el inicio de cada edición (2.22).
- **Errores por causa, no por tipo de Java**: al principio la API traducía
  `IllegalArgumentException` a 400 y `IllegalStateException` a 409, y con eso
  un id inexistente o un dato guardado roto salían como 400 o 409, igual que
  un pedido mal formado. Hoy cada causa tiene su excepción y `ApiExceptionHandler`
  responde siempre con `ErrorDto {error, details}`: `NotFoundException`
  (application) → 404; `ConflictException` (domain) y su subclase
  `ConcurrentModificationConflict` (puertos) → 409; `BusinessRuleViolation` y
  `TeamIneligibleException` (domain) → 422; `CorruptDataException`
  (infrastructure) e `InconsistentStateException` (application, una referencia
  rota entre agregados guardados) → 500 con mensaje genérico y la causa en el
  log; base caída → 503. `IllegalArgumentException` (dato mal formado) sigue
  siendo 400 e `IllegalStateException` (transición que el estado no acepta)
  sigue siendo 409. Los rechazos esperables que devuelven un resultado sellado
  (`Publication`, `Reception`, `AppealAcceptance`, `StandingsPublication`) los
  traduce cada controller con un `switch` (README, "Convenciones de la API").

---

### 2.20 Tiempo e identidades generadas como dependencias explícitas

- **Problema**: Las llamadas al reloj del sistema y a UUID en el núcleo hacían
  depender fixtures, auditoría y fechas del host y del momento de ejecución.
- **Solución**: Los puertos `com.roboleague.support.Clock` e `IdGenerator` viven
  en domain. `SystemClock` y `UuidGenerator` viven en infrastructure y se ensamblan
  en `UseCaseConfig`, salvo en el perfil demo, que usa `DemoClock` y `DemoIds` (2.30). La propiedad `roboleague.time-zone`, configurable con
  `ROBOLEAGUE_TIME_ZONE`, usa `America/Argentina/Buenos_Aires` por defecto.
  El núcleo no consulta estáticamente el reloj ni genera UUID por su cuenta.
- **Valores en los agregados**: Registration, Documentation, Appeal y Standings reciben
  fechas/horas explícitas; las transiciones de Attempt reciben `OperationAudit`
  con hora y dos ids de evento preparados por el caller (una transición genera
  como máximo dos eventos). Revisiones y eventos de una misma operación comparten
  hora; los ids de snapshots siguen derivados de intento y número de revisión.
  Una captura parcial o una descalificación usa solo el primer id, dejando el
  segundo sin uso; consumir un id no implica que haya un evento persistido.
- **Rehidratación y calendario**: Mappers conservan tiempos, ids y versiones
  existentes sin recurrir a puertos. `TeamMember.of` valida nacimiento contra
  una fecha explícita; su constructor de valor conserva datos estructurales al
  rehidratar. La spec 03 fijó la referencia de elegibilidad al inicio de cada
  edición y conservó el momento de inscripción separado de esa fecha (2.22).
- **Trade-off**: Las firmas del núcleo exigen más valores explícitos y los
  callers/fixtures deben suministrarlos. Se conserva el formato LocalDateTime
  sin offset, las columnas y el JSON histórico; configurar otra zona afecta
  nuevas operaciones y no convierte timestamps anteriores.
- **Verificación**: Reloj fijo y secuencia local de ids en el flujo de competencia,
  prueba de cambio de fecha por zona y pruebas de rehidratación/JPA y JSON de API.
  Los escenarios que consultan el ranking más reciente avanzan explícitamente
  su reloj entre cálculos; un reloj fijo no garantiza orden entre timestamps iguales.

### 2.21 Identidades tipadas y contratos externos textuales

- **Problema**: Los ids String de equipos, jueces, slots y otros conceptos podían
  intercambiarse sin que el compilador detectara el error, incluso en puertos y
  mapas internos. ChallengeId y AttemptId ya expresaban parte de esta intención.
- **Decisión**: Records distintos por concepto, sin jerarquía universal ni
  dependencias de frameworks. TeamId, EditionId, CategoryId, ParticipantId y
  RobotId viven en tournament; JudgeId, SlotId, RoundId y TrackId en scheduling;
  StandingsId y AppealId en ranking (StandingsId se deriva de desafío y categoría
  desde 2.26; antes era RankingId). Se reutilizan ChallengeId y AttemptId,
  que ahora contiene SlotId y número positivo. Los valores rechazan null/blanco,
  conservan espacios y mayúsculas de valores válidos y no exigen UUID.
- **Alcance**: Entidades, profiles, scopes, commands, repositorios y claves de
  mapas de equipos y jueces usan los tipos. IdGenerator sigue entregando texto;
  quien genera un slot, ronda o apelación lo envuelve inmediatamente.
  El desempate final conserva el orden textual mediante Comparable<TeamId>.
  (El filtro opcional de ronda del `Ranking` de la Entrega 1 desapareció con
  2.26: la tabla es por desafío y categoría y cubre todas sus rondas.)
- **Autores**: Capturas y descalificaciones inequívocamente realizadas por jueces
  usan JudgeId. Auditoría, ajustes, revisión de apelaciones y verificación de
  documentación usan ActorId, porque el responsable puede tener distintos roles.
  JudgeId.asActorId() expresa conscientemente al juez como autor; no se convierte
  automáticamente un actor genérico en juez.
- **Bordes**: DTOs HTTP, entidades JPA y records JSONB mantienen campos String.
  Controllers y mappers convierten mediante of/parse/value; las claves del panel
  de jueces también se convierten explícitamente. No se anotan los records del
  núcleo con Jackson/JPA ni se deja su serialización automática definir la API.
  Columnas, claves primarias, formato de AttemptId e historial conservan sus
  representaciones; no hace falta una migración SQL por este cambio de tipos Java.
- **Trade-off**: Cambian las firmas internas y los fixtures necesitan valores
  tipados. Se gana detección de referencias intercambiadas al compilar, a costa
  de conversiones explícitas en los adaptadores. No se tipa cada etiqueta:
  nombres, motivos, métricas nombradas y los ids internos de eventos/snapshots
  conservan sus contratos actuales, fuera del alcance de esta migración.
- **Verificación**: El compilador rechaza un JudgeId donde SlotIdentity exige
  TeamId. Tests cubren valores inválidos, conservación de texto histórico,
  parsing con guiones, rehidratación desde una fila textual preexistente,
  búsquedas y escritura con las mismas claves, además de contratos HTTP de ids.

### 2.22 Registration y elegibilidad sostenida (spec 03)

- **Problema**: Edition retenía Team mutable y este llevaba una categoría global.
  Cambiar integrantes, robot o documentación podía invalidar una inscripción sin
  control, y el calendario de elegibilidad se fijaba al iniciar Spring.
- **Modelo**: Registration es una entidad inmutable dentro de Edition, con
  identidad compuesta EditionId/TeamId, CategoryId, referenceDate y registeredAt.
  La referencia es Edition.startDate; el momento de inscripción viene del Clock.
  Categoría y fecha salen de Team. TeamRepository guarda su estado canónico;
  Edition contiene relaciones y nunca una segunda lista de equipos.
- **Protección del estado**: Team, Robot, Documentation y Edition son inmutables;
  sus constructores copian colecciones. Métodos with... y operaciones de edición
  devuelven nuevos valores. Cambiar documentos invalida su verificación porque
  esta correspondía al conjunto anterior; verificar el candidato suministra autor
  y hora explícitos. Ningún cambio del candidato modifica el equipo ya guardado.
- **Política de dominio**: Las especificaciones componen EligibilityCandidate
  (equipo, categoría y fecha), reuniendo causas de edad, tamaño, robot y
  documentación. RegistrationEligibility se usa desde Edition en altas, cambios
  de categoría y revalidación. TeamIneligibleException vive en domain, conservando
  su traducción HTTP a 422 en API.
- **Coordinación**: RegisterTeamUseCase distingue Team nuevo de TeamId existente
  y rechaza sobrescribir estado canónico mediante un alta. Duplicados en una
  edición son conflictos. UpdateTeamUseCase consulta EditionRepository.findByTeamId,
  valida el candidato contra todas las relaciones y reúne motivos por edición
  antes de guardar. ChangeRegistrationCategoryUseCase modifica una relación y
  conserva sus tiempos. Programación revalida antes de generar ids; rankings
  resuelven los participantes actuales sin cambiar cálculos del frente 4.
- **Un integrante y un robot por equipo en cada edición**: `EditionRoster`
  (`tournament.eligibility`) recibe los equipos inscriptos en una edición y nombra
  cada integrante (`ParticipantId`) o robot (`RobotId`) que un candidato comparte
  con otro equipo; la propia inscripción del candidato no cuenta. Inscribir un
  equipo nuevo o canónico y actualizar uno inscripto lo consultan antes de
  guardar, y los conflictos vuelven como motivos de `TeamIneligibleException`
  (422), junto con los de elegibilidad en una actualización. No es una
  especificación más porque mira otros agregados: `EligibilityCandidate` sigue
  siendo equipo, categoría y fecha. La misma persona o robot puede competir en
  otra edición. *Límite*: la identidad es el id que manda el cliente; dos ids
  distintos para la misma persona no se detectan.
- **Rehidratación**: Edition.restore valida pertenencia, categorías, fecha de
  referencia y unicidad, conservando los tiempos sin reevaluar equipos. Team
  admite construcción completa de su estado; Documentation.restore conserva
  documentos y verificación sin replay. La clave de Registration es
  edición/equipo; los mappers de 2.27 preservan estos valores.
- **Alternativas**: No se agregó un cierre de inscripción, pues no existe ese
  ciclo de vida. Copias defensivas de un Team mutable habrían conservado múltiples
  fuentes de estado; bloquear todos los cambios impediría actualizaciones válidas.
  Se eligieron snapshots inmutables con validación coordinada de inscripciones.
- **Límites**: Validar antes de escribir evita cambios parciales por rechazo.
  Repositorios/restore son mecanismos confiables de persistencia, no entradas de
  negocio. JPA y control de escrituras concurrentes llegaron en 2.27. Endpoints
  de inscripción/cambios/programación fueron implementados en la spec 05 (2.24).
- **Verificación**: Calendario con edades que cambian elegibilidad al iniciar la
  edición, rechazos sin escrituras, duplicados, relaciones independientes,
  actualizaciones válidas e inválidas contra dos categorías, inmutabilidad y
  rehidratación, revalidación al programar, rankings canónicos, proxies y demo.

### 2.23 Rondas por desafío y programación paralela (frente 3, spec 04)

- **Problema**: El scheduler usaba un horario serial y asignaba dos jueces sin
  comprobar disponibilidad. La ronda no conocía el desafío y cualquier consumidor
  podía cambiar slots sin controles de pertenencia, solapamiento o lifecycle.
- **Límite de agregado (DDD)**: Round contiene Slot y controla sus operaciones;
  un SlotId sigue siendo global, pero no crea otro repositorio. RoundScope contiene
  ChallengeId/EditionId/CategoryId/número positivo. El caso de uso consulta Challenge
  y Edition para verificar pertenencia, categoría y elegibilidad antes de generar
  ids. Las referencias por identidad evitan incorporar otros agregados al ciclo
  de vida de la ronda.
- **Estado protegido**: Round/Slot son snapshots inmutables; las operaciones
  devuelven una nueva ronda. Se rechazan slots ajenos, ids/equipos duplicados y
  solapamientos de pista o juez. Los intervalos son semicerrados. Round y Slot
  pasan de SCHEDULED a IN_PROGRESS y COMPLETED; solo un slot SCHEDULED puede
  cancelarse. La ronda termina con todos los slots completados o cancelados.
  Las fuentes recibidas por Attempt no provocan estas transiciones.
- **Servicio de dominio**: RoundSchedulerService recibe equipos ordenados por
  registeredAt/TeamId y rondas guardadas. Evalúa combinaciones pista/juez en orden,
  busca la ventana libre más temprana avanzando al fin de cada conflicto y asigna
  un juez. SlotConflicts centraliza la regla compartida con el agregado. Se
  comparan ids, no igualdad de perfiles. Pistas inactivas se excluyen y recursos
  duplicados se rechazan. Las ventanas completas permiten respetar ocupaciones
  futuras y aprovechar huecos anteriores.
- **Pausa de pista**: Slot conserva trackInterval. Pista ocupada hasta fin +
  pausa; juez/equipo hasta fin. También se considera la pausa del candidato para
  no invadir un turno futuro. CANCELLED libera recursos; COMPLETED conserva la
  ocupación histórica. No extender la pausa del juez ni deducirla del request de
  otra ronda, porque cambiaría las restricciones del horario ya guardado.
- **Puertos y adaptadores (DIP/hexagonal)**: RoundRepository incorpora findById,
  findByScope, findByChallengeId y findAll, conservando findBySlotId. Memory guarda
  la ronda completa, reemplaza por id y rechaza scope o SlotId usados por otra
  ronda. Round.restore/Slot.restore conservan scope, estados, asignaciones,
  horarios y pausa sin replay. Domain/application siguen sin frameworks.
- **Captura y demo**: ReceiveResultUseCase verifica desafío de ronda antes de
  abrir/cambiar Attempt, incluso en la primera captura (409). Se conserva el
  request existente, scoring y auditoría. La demo indica desafío al programar y
  usa al juez asignado para ambas fuentes; notas del panel no equivalen a
  disponibilidad/asignación de sus miembros.
- **Alternativas**: Mantener Slot mutable permitiría saltear Round por un getter.
  Repositorios separados debilitarían el límite de consistencia. Clases State
  por enum no aportan aquí comportamientos variables como en Attempt/Appeal;
  alcanzan transiciones explícitas protegidas por Round. El algoritmo vive en
  dominio y los casos de uso solo cargan, coordinan y guardan (SRP).
- **Límites y verificación**: Memory no ofrece durabilidad, rollback ni exclusión
  entre programaciones concurrentes. La validación secuencial no equivale a un
  lock transaccional. JPA y scope único llegaron en 2.27; la coordinación sigue pendiente.
  Se prueban paralelismo, pausas, límites semicerrados, conflictos entre rondas,
  recursos por identidad, rechazos sin guardado, lifecycle, restore, contrato
  memory, captura, proxies y continuidad de la demo/rankings.

### 2.24 API de inscripción y programación (frente 3, spec 05)

- **Objetivo**: Operar ediciones, desafíos, inscripciones y rondas desde contratos
  HTTP, sin conocer fixtures ni consultar adaptadores desde controllers. Las
  consultas son entradas de application y devuelven datos que API transforma en
  DTOs. No se introdujeron Spring/Jackson en el núcleo.
- **Consultas (SRP/DIP)**: QueryEditionsUseCase, ListEditionChallengesUseCase,
  QueryRegistrationsUseCase y QueryRoundsUseCase validan padres, relaciones y
  filtros. RegistrationView reúne Registration y el Team canónico actual; no
  guarda una copia dentro de Edition. ChallengeRepository agrega findByEditionId,
  implementado en ambos adaptadores con orden estable. Un padre desconocido es
  404 (antes 400); una lista vacía es 200; un filtro de categoría ajena es 400.
- **Actualización conjunta**: PUT recibe categoryId y Team completo. El nuevo
  UpdateRegistrationUseCase prepara la Edition candidata con esa categoría y
  valida el equipo contra todas sus inscripciones, sustituyendo la edición
  destino por la candidata. Comparte la validación coordinada con UpdateTeamUseCase.
  Guarda Team y Edition solo después de validar; conserva fechas de inscripción.
  No se encadenan dos mutaciones desde el controller, porque el primer cambio
  podría quedar guardado si el segundo fuera rechazado, ni se valida contra la
  categoría anterior cuando el usuario está cambiando ambos datos juntos.
- **Transporte**: TeamBody construye el candidato con valores tipados, fechas de
  creación/verificación del Clock del servidor y actor de verificación recibido.
  No hay upload/autenticación en este paso. POST permite objeto Team nuevo o
  TeamId existente, exactamente uno; un objeto de id existente no sobrescribe.
  Los records usan wrappers para reconocer campos numéricos/booleanos faltantes,
  y comprueban elementos null antes de construir valores. RequestValues se limita
  a presencia/formato; elegibilidad y programación siguen siendo dominio.
- **Compatibilidad**: EditionDto conserva categories como ids y suma categoryDetails
  con restricciones. Alta de categoría permite límites dimensionales opcionales
  con default histórico de 1000 mm por eje. Weight/Dimensions rechazan NaN/infinito
  en sus constructores, evitando cantidades físicas inválidas. Las respuestas de
  inscripción incluyen auditoría, pero esos campos no se aceptan en requests.
- **Programación HTTP**: RoundController deriva edición del desafío; no recibe un
  EditionId contradictorio. RoundDto incluye scope, estado y slots ordenados por
  horario/TrackId/SlotId, sin exponer entidades internas. Sus slots se usan en los
  endpoints existentes de ambas fuentes de captura y del desglose.
- **Cableado y límites**: Todos los nuevos casos de uso conservan proxies de clase
  de TransactionalUseCases. Rechazos de validación no dejan cambios parciales.
  Equipos, ediciones y rondas pasaron a Postgres en 2.27. Los endpoints de
  tablas y apelaciones llegaron en 2.26, y los de corrección de intentos y
  ciclo de vida de rondas en 2.29.
- **Verificación**: MockMvc sobre Postgres/Testcontainers cubre creación, lectura,
  actualización conjunta, revalidación entre ediciones, programación, consumo de
  slots en captura y desglose, listas vacías y 400/409/422. Casos de uso prueban
  validación conjunta antes de escribir; ambos adaptadores de desafíos prueban
  filtro/orden por edición. La demo y las pruebas de proxies permanecen activas.

### 2.25 Front web servido por API (frente 3, spec 06 parcial)

- **Decisión**: HTML/CSS y módulos ES nativos en resources/static, con el mismo
  jar/puerto que la API. Sin build Node, framework frontend, CDN ni autenticación
  simulada. Es un adaptador de entrada; no agrega scoring/elegibilidad al browser.
  Diseño sobrio, neutro, para escritorio, con selección visible de contexto y acciones
  de competencia en navegación lateral.
- **Responsabilidades (SRP/hexagonal)**: api.js concentra HTTP/ErrorDto; ui.js
  construye nodos seguros y controla envíos; models.js traduce contratos, referencias
  y presentación; registration/scheduling/results presentan flujos. app.js carga
  estado del backend y coordina contexto/navegación, descartando respuestas tardías
  tras un cambio de contexto. No mantiene otro modelo de dominio ni estado de
  competencia durable en el browser.
- **Contratos y referencias**: Formularios normales, sin JSON manual. Team/miembro/
  robot/recurso nuevos reciben ids en el candidato; editar conserva identidades.
  Auditoría de respuesta no se reenvía como request. Recursos conocidos entre
  rondas/desafíos conservan ids. El servidor confirma cambios, horarios y puntajes.
- **Captura histórica**: Se agregó GetRulebookUseCase y GET de versión publicada.
  Un breakdown identifica la versión del intento y la web consulta sus métricas,
  evitando usar la definición actual para una segunda fuente histórica. La consulta
  es de solo lectura y usa ChallengeRepository; no reescribe reglamentos.
- **Uso y errores**: Métricas se presentan por fuente, unidad y rango declarado.
  El desglose usa ítems/fórmulas/topes/revisiones del backend y muestra ausencia
  de puntaje como pendiente/sin puntaje computable. Los 422 conservan candidatos;
  un 409 bloquea mutaciones hasta recargar, sin retries automáticos. La selección
  de contexto se bloquea durante un envío. Renderizado por texto evita interpretar
  nombres/motivos como HTML.
- **Límites explícitos**: Apelaciones y tabla ya consumen los contratos del frente 4
  (2.26). Equipos, ediciones y rondas se guardan en Postgres desde 2.27. La
  comprobación visual en navegador sigue pendiente.
- **Verificación**: Tests JS de contratos/cliente HTTP sin dependencias npm,
  añadidos a CI; Maven cubre assets, versión histórica, demo, apelaciones y standings.
  El navegador de la sesión no estuvo disponible: no se acredita validación visual,
  layout de escritorio, teclado o flujos desde navegador; quedan documentados en README.

### 2.26 Tabla versionada, publicación y ciclo de apelaciones (frente 4)

- **Problema**:
  La Entrega 1 agrupaba por edición/categoría/ronda con `Ranking` + `TieBreakerChain`
  y publicaba esa foto. El hallazgo 5 permitía oficializar una tabla vieja; el 8
  no declaraba el desempate en el reglamento; el 3 no contaba apelaciones abiertas.
  Frente 3 necesitaba consultar, recalcular y publicar por desafío, y el ciclo
  completo de apelaciones (presentar, revisar, aceptar/rechazar).

- **Solución Implementada**:
  - **`Standings` por desafío y categoría** (`StandingsId`). Cada recálculo agrega
    una `StandingsVersion` y no edita las anteriores. El orden lo decide
    `StandingsTable` con el `RankingScheme` vigente del desafío: ronda por ronda
    (`TeamRounds` / `RoundResult`) y `decide` para explicar el puesto (`Placement`).
  - **Publicar es un resultado** (`StandingsPublication.Published` | `Blocked`):
    solo la última versión, y solo si `PublicationCheck` ve la misma tabla, cero
    apelaciones abiertas y cero turnos sin resultado. Publicar reemplaza a la
    oficial anterior (`OfficialPublication`); la vieja queda `REPLACED`.
  - **Apelación sin setters públicos**: las transiciones siguen en `AppealState`.
    Aceptar lleva correcciones `SourceReport` y las puntúa el intento con su
    reglamento (`AppealAcceptance`). Rechazar restaura el intento; con dos
    apelaciones abiertas, una sola no lo libera.
  - **API**: `POST /attempts/{id}/appeals`, `GET` por intento/categoría,
    `POST /appeals/{id}/review|acceptance|rejection`, `GET/POST` standings y
    `POST .../publication`. Un bloqueo o una corrección inválida no escriben.
  - **Persistencia**: `JpaStandingsRepository` (migración `V5`) con versión
    optimista, igual que el intento. `Appeal` ya estaba en Postgres.
  - **Versión del reglamento de cada tabla**: `StandingsTable(rankedWith, filas)`
    guarda la `RulebookVersion` cuyo `RankingScheme` la ordenó; `CategoryResults`
    lleva el reglamento vigente completo. Así una versión de la tabla dice con qué
    reglas se calculó (la consigna pide recalcular con la versión correspondiente),
    la API la muestra en `rulebookVersion`, y publicar una versión ordenada con un
    reglamento anterior al vigente se bloquea con su propio motivo: la tabla se
    recalcula con el nuevo antes de oficializarse. El JSON de `standings` guarda
    `rankedWith`; la migración `V10` lo completa en las filas existentes con la
    versión vigente al migrar, que es la que las ordenó salvo que el desafío haya
    publicado otra después del cálculo (ese caso no se distingue con lo guardado).
    Se descartó guardar la versión en `StandingsVersion`: es un dato de cómo se
    ordenó la tabla, y la tabla es lo que `PublicationCheck` compara.

- **Por qué no se reutilizó `Ranking`**: el alcance cambió (desafío, no ronda
  suelta) y el recálculo tenía que ser append-only. Evolucionar el agregado
  viejo mezclaba dos invariantes. El reglamento ya tenía el esquema; faltaba
  el consumidor (regla #27).

- **Alternativas descartadas**:
  - Editar la última fila al recalcular: alguien ya pudo ver esa versión.
  - Publicar cualquier versión histórica: hallazgo 5.
  - Contar apelaciones en un servicio con `if` de estado: el intento ya las cuenta.
  - Recalcular en el navegador: el front es adaptador (2.25).

- **Clases agregadas**: `Standings`, `StandingsVersion`, `StandingsTable`,
  `PublicationCheck`, `StandingsPublication`, `CategoryResults`,
  `RecalculateStandingsUseCase`, `PublishStandingsUseCase`, `QueryStandingsUseCase`,
  `QueryAppealsUseCase`, `StandingsController`, `JpaStandingsRepository`.
- **Clases eliminadas**: `Ranking`, `RankingCalculatorService`, `TieBreakerChain`,
  `TeamScore`, `PublishOfficialRankingUseCase`, `RecalculateRankingUseCase`.

### 2.27 Persistencia de ediciones, equipos y rondas (frente 5)

- **Problema**: `Edition`, `Team` y `Round` vivían en memoria mientras desafíos,
  intentos, apelaciones y standings estaban en Postgres. Al reiniciar, esas filas
  apuntaban a ediciones, equipos y rondas inexistentes, y la transacción del caso
  de uso no cubría sus escrituras.

- **Solución Implementada**:
  - **Una tabla por agregado** (`editions` V6, `teams` V7, `rounds` V8), con la
    identidad y los datos escalares en columnas y lo anidado en JSONB:
    torneo/categorías/inscripciones, robot/integrantes/documentación y slots
    con pista, jueces, ventana, pausa y estado. Igual que 2.14: cada agregado se
    lee y se escribe entero; la forma del JSON la fijan records del adaptador.
  - **Rehidratación sin replay**: `Edition.restore`, el constructor de `Team`,
    `Documentation.restore`, `Round.restore` y `Slot.restore` conservan ids,
    `registeredAt`, verificación y estados. No se agregaron fábricas al dominio.
  - **Consultas**: `findByTeamId` y `findBySlotId` usan contención JSONB con
    índices GIN. `rounds_scope_uk` asegura el scope único; el SlotId global se
    comprueba en el adaptador, como en memoria.
  - **Concurrencia**: `@Version` en las tres tablas. Como los agregados son
    inmutables, la instancia guardada nunca es la leída y no sirve recordar la
    versión por instancia (2.18). El adaptador toma la versión que el contexto de
    persistencia leyó en la misma transacción: si otro pedido guardó en el medio,
    la escritura se rechaza con `ConcurrentModificationConflict` (409), como en
    los otros cuatro adaptadores (por ejemplo, dos inscripciones simultáneas).
  - **Cableado**: se eliminó `InMemoryRepositoryConfig`; la API descubre los
    siete `Jpa*Repository`. Los adaptadores en memoria quedan para tests de
    casos de uso, con el mismo orden por id que JPA.

- **Alternativas descartadas**:
  - Tablas hijas para inscripciones, integrantes o slots: nadie los consulta por
    separado y obligaban a sincronizar colecciones del agregado.
  - Mapa de versiones por instancia: no funciona con agregados inmutables.

- **Límites**: dos programaciones concurrentes de rondas distintas pueden tomar
  la misma pista o juez; cada una lee las demás rondas antes de guardar y nada
  las serializa. Un agregado que no se leyó en la transacción se escribe sobre
  la versión vigente. Los SlotIds se generan con `IdGenerator`, sin restricción
  única en Postgres.

- **Clases agregadas**: `EditionJpaEntity`, `EditionMapper`, `SpringDataEditions`,
  `JpaEditionRepository`, `TeamJpaEntity`, `TeamMapper`, `SpringDataTeams`,
  `JpaTeamRepository`, `RoundJpaEntity`, `RoundMapper`, `SpringDataRounds`,
  `JpaRoundRepository`.
- **Clases eliminadas**: `InMemoryRepositoryConfig`.
- **Clases modificadas**: `InMemoryEditionRepository`, `InMemoryTeamRepository`.

### 2.28 Puntos exactos (`Points`)

- **Problema**: los puntajes eran `double` y una suma en punto flotante decidía
  la tabla. `10.1 + 20.2 + 30.3` no es igual a `30.3 + 20.2 + 10.1`: dos equipos
  con las mismas rondas en otro orden quedaban separados por "Mayor puntaje
  total" y la cadena de desempate del reglamento no llegaba a correr. Es el mismo
  problema que un importe de dinero en `double`.

- **Solución Implementada**:
  - **`Points`** (`evaluation`): objeto de valor inmutable sobre `BigDecimal` con
    escala fija 2 y `RoundingMode.HALF_EVEN`. La escala se normaliza al construir,
    así `equals`, `hashCode` y `compareTo` coinciden (10 = 10.0 = 10.00). Ofrece
    `plus`, `minus`, `negate`, `abs`, `times`, `max`, `min` y fábricas que
    rechazan NaN e infinitos.
  - **Escala 2 (centésimas)**: los reglamentos expresan puntos con a lo sumo dos
    decimales y el desglose ya los muestra así; una tercera cifra sugeriría una
    precisión que ninguna regla usa. **HALF_EVEN** no sesga hacia arriba una suma
    de muchos ítems redondeados.
  - **Se redondea una vez, en el ítem**: lo que no cae en centésimas es el
    producto que arma el subtotal de una regla (tiempo × tasa, máximo × precisión,
    promedio de jueces × peso), calculado en `BigDecimal` desde el decimal más
    corto de cada `double`. Sumas de secciones, tope, piso en cero, rondas y
    totales de la tabla son exactos.
  - **Son `Points`**: subtotal de `ScoreItem`; totales de `RuleEvaluation`,
    `ScoreBreakdown` (total, descontado e ítem del piso), `SourceContribution`,
    `RoundScore`, `ChallengeScore`, `RoundResult`, `TeamRounds` y `StandingsEntry`;
    `BonusLimit.limit` y `CappedAt.maximum`. `HigherTotal`, `LowerDeductions`,
    `BestNOfM`, `TeamAttempts` y el empate de `StandingsTable` comparan `Points`.
  - **Parámetros que son puntos** (`basePoints`, `minPoints`, `pointsPerObjective`,
    `maxPoints`, `pointsPerRescued`, `bonus`, deducciones por falta, por víctima y
    por penalización, tope): `Parameters.points` rechaza más de dos decimales al
    publicar, para no redondear en silencio un reglamento publicado.
  - **Siguen `double`**: las mediciones (tiempo, consumo, métricas con nombre,
    precisión) y los factores (peso de jueces, tasas por segundo o por unidad de
    consumo); no son puntos y no se suman entre equipos. Las notas de jueces
    también: son una nota sobre una escala, y la regla las convierte en `Points`.
    Sus promedios (por intento y entre rondas, que usa `HigherJudgeScore`) se
    suman en `BigDecimal` para no depender del orden.
  - **Bordes**: los DTOs exponen `doubleValue()`, así el JSON de la API no cambia
    (`147.5`). El JSONB de intentos y tablas guarda `BigDecimal` y lee los números
    ya guardados. `Attempt.getFinalScore()` se eliminó: solo lo usaban tests.

- **Alternativas descartadas**:
  - Comparar `double` con tolerancia: no es transitivo y el empate dependería de
    un umbral arbitrario.
  - `long` en centésimas: exacto, pero esconde la escala en cada fórmula y texto.
  - `BigDecimal` suelto: `equals` distingue 10.0 de 10.00 y nada impone escala
    ni redondeo.

- **Deuda**: `Parameters`, `RawMetrics` y los contratos JSON siguen en `double`;
  un puntaje en centésimas viaja sin pérdida, pero el tipo no lo expresa. La
  escala es global, no configurable por reglamento.

### 2.29 Modelo sin código muerto: ajustes, descalificación y cancelación de turnos

- **Problema**: parte del modelo no tenía ningún llamador de producción, solo
  tests: el ajuste de faltas y la descalificación de `Attempt`, el ciclo de
  vida de `Round`/`Slot`, `DateRange.includes` y los datos del torneo de una
  edición. Modelaban algo que después nadie usaba. Además, un
  equipo que no se presentaba dejaba su turno sin resultado para siempre, y
  `PublicationCheck` bloqueaba la publicación de la tabla sin salida.

- **Solución Implementada**:
  - **Correcciones fuera de una apelación** (`CorrectAttemptUseCase`, un caso de
    uso con dos operaciones, como `ResolveAppealUseCase`): `adjustPenalties`
    carga el intento, aplica las faltas con un `AuditNote` (autor y motivo) y la
    hora del `Clock`, guarda y recalcula la tabla de su desafío y categoría;
    `disqualify` exige un juez asignado al turno, como la captura, y también
    recalcula, porque un intento descalificado deja de contar. Ambas devuelven
    `AttemptCorrection {attempt, standings}`; la API responde
    `{attempt, standingsVersion}` igual que la aceptación de una apelación.
  - **Validar antes de mutar**: `applyPenaltyAdjustment` construía el
    `PenaltyDetail` (que rechaza cantidades no positivas) después de agregar la
    revisión, y dejaba una revisión sin evento. Ahora lo construye primero.
  - **`disqualify(AuditNote, OperationAudit)`**, como las demás transiciones. La
    API recibe un `JudgeId` y lo convierte con `asActorId()`;
    `AttemptDisqualifiedEvent` guarda un `ActorId` (el JSON persistido ya
    guardaba el texto del actor, así que no cambia).
  - **Ciclo de vida de rondas** (`RoundLifecycleUseCase`): empezar y completar la
    ronda, empezar, completar y cancelar cada turno, con subrecursos `POST`. Se
    cableó completo en vez de borrar `start`/`complete`: la spec 04 define esos
    estados, Postgres y el front ya los guardan y muestran, y completar una ronda
    es justamente lo que exige turnos terminados o cancelados.
  - **Cancelar deja rastro**: `Slot` guarda una `SlotCancellation` (actor,
    motivo, hora) solo si está `CANCELLED`, y `Slot.restore` lo valida. Viaja en
    el JSONB de `rounds` como campo opcional, sin migración (ninguna fila previa
    tenía turnos cancelados por la API). `PublicationCheck` ya ignoraba los turnos
    cancelados; ahora hay una forma de cancelarlos. Un turno cancelado no acepta
    resultados (409) y uno con resultados no se cancela: se descalifica el intento.
  - **Fechas de la edición**: `DateRange.includes` no tenía uso. Ahora
    `Round.happensWithin(DateRange)` lo usa y `ScheduleRoundUseCase` rechaza con
    `BusinessRuleViolation` (422) una ronda con turnos fuera de la edición.
  - **Datos escritos y nunca leídos**: el torneo, la temporada y el número de
    edición se guardaban pero `EditionDto` no los devolvía. Ahora los expone, y
    `editionNumber`/`season.year` son `Integer` obligatorios en el request: un
    valor omitido ya no se convierte en 0 en silencio.
  - **Una sola vía de elegibilidad**: `AgeLimitSpecification` y
    `TeamSizeSpecification` repetían con primitivos lo que ya decidían
    `AgeRange.contains` y `TeamSizeRange.allows`; ahora delegan en ellos y solo
    agregan el mensaje (los mismos textos de antes). El robot ya delegaba en
    `RobotLimits.checkViolations`. Se borró la vía paralela sin uso:
    `Category.allowsAge/allowsTeamSize/allowsRobot`, `RobotLimits.allows`,
    `RobotSpecification.fitsWithin`, `Dimensions.fitsWithin` y los accesores
    planos de `Category` (`minAge()`, `maxRobotWeightGrams()`...); el DTO y el
    mapper leen `category.restrictions()`.
  - **Borrado lo que nada usaba**: `EligibilitySpecification.or/not`,
    `Weight.ofGrams`, `Appeal.getTarget/getClaim/getState`,
    `TimeBasedRule.getConfig`, `CompositeScoreRule.getRules`,
    `EvaluationDetails.of`, `PenaltyRule.standard`, `JudgeSubjectiveRule.standard`,
    `Robot.withSpecification`, `PenaltyAppliedEvent.create`,
    `ScoreAdjustedEvent.newMetrics/newScoreBreakdown` y
    `ResultRegisteredEvent.metrics/scoreBreakdown` (el mapper persiste la
    `EvaluationSnapshot` del evento, no esos atajos). `Attempt.getFinalScore` y
    `getOriginalSnapshot` solo los usaban tests, que ahora leen
    `getScoreBreakdown()` y `getRevisionHistory()`. Las sobrecargas de prueba
    `RawMetrics.of(tiempo, objetivos, faltas[, notas])` y
    `EvaluationFeedback.withMeasurements/withJudgeScores` pasaron a
    `TestValues` del dominio.
  - **Lo que se conserva sin llamador de producción, y por qué**: las fábricas
    `of` de objetos de valor (`AgeRange.of`, `DateRange.of`, `Slot.of`,
    `TimeTargets.of`...), convención del modelo para construirlos;
    `ScoringScheme.withoutBonuses`, que nombra un esquema real (sin
    bonificaciones) y arma los reglamentos de casi todos los tests; los
    `Team.with...` y `Documentation.revokeVerification`, la API de candidatos
    inmutables documentada en el dominio; y `Judge.certificationLevel`, que se
    persiste en el JSONB de `rounds`.

- **Alternativas descartadas**:
  - Borrar `applyPenaltyAdjustment` y `disqualify`: la consigna pide corrección de
    resultados con auditoría y que el recálculo refleje la corrección.
  - Un caso de uso por transición de ronda: cinco clases con la misma carga y
    guardado; se agrupan como las de una apelación.
  - Cancelar sin autor ni motivo: sería la única transición sin auditoría.
  - Mover todo método sin llamador a una clase de soporte de tests: las
    fábricas de valor y los candidatos inmutables son parte del lenguaje del
    modelo; se movieron solo las sobrecargas de conveniencia.

- **Deuda**: el ciclo de vida de la ronda es informativo; no impide capturar
  resultados en un turno `SCHEDULED` (spec 04: el ciclo de vida no sigue a las
  fuentes). No hay autenticación: `actorId` y `judgeId` se confían al pedido.

- **Clases agregadas**: `CorrectAttemptUseCase`, `AttemptCorrection`,
  `RoundLifecycleUseCase`, `SlotCancellation`.
- **Clases modificadas**: `Attempt`, `AttemptDisqualifiedEvent`, `Round`, `Slot`,
  `AgeLimitSpecification`, `TeamSizeSpecification`, `Category`, `RobotLimits`,
  `ReceiveResultUseCase`, `ScheduleRoundUseCase`, `AttemptController`,
  `RoundController`, `EditionController`, `RoundMapper`, `EditionMapper`.

### 2.30 Demo determinista y completa

- **Problema**: la demo declaraba lo que pide la consigna pero no lo mostraba.
  Todos los reglamentos tenían mejores N de M y cada equipo corría una sola
  ronda, así que `discarded` salía siempre vacío; la cadena de tres criterios
  nunca decidía nada porque no había empates en puntaje. Además, el README decía
  que cada corrida terminaba en el mismo estado, pero rondas, turnos, apelaciones
  y eventos tomaban UUID aleatorios y las horas salían del reloj del sistema. El
  javadoc de `DemoRulebooks` hablaba de diez tipos de regla y son doce.

- **Solución Implementada**:
  - **Laberinto con cuatro rondas y tres equipos** (mejores 3 de 5). Se sumó
    NovaTeam (`t-c`). Los puntajes están elegidos para que la tabla oficial
    (v2, después de la apelación) deje a los tres en 717 puntos: TitanTeam gana
    por menor tiempo (45 s contra 50 s) y CyberTeam supera a NovaTeam, con el
    mismo mejor tiempo, por menor descuento (2 contra 16 puntos). La
    explicación de cada puesto nombra el criterio que decidió. CyberTeam y
    TitanTeam descartan su ronda 4; en la v1 TitanTeam descartaba la ronda 2,
    la de las cuatro faltas, y la apelación cambia cuál descarta. La apelación
    sigue moviendo a TitanTeam del tercer al primer puesto.
  - **Turno cancelado**: NovaTeam no se presenta en la ronda 4 y su turno se
    cancela con `RoundLifecycleUseCase` (autor y motivo). No bloquea la
    publicación y muestra a un equipo con tres rondas, sin descarte.
  - **Reloj e ids propios del perfil** (`DemoClock`, `DemoIds` en
    `api.demo`). `UseCaseConfig` declara `SystemClock` y `UuidGenerator` con
    `@Profile("!demo")` y `DemoConfig` aporta los suyos: no hay dos beans para
    el mismo puerto y el resto de los perfiles no cambia. `DemoIds` da
    `demo-1`, `demo-2`... en orden de pedido. `DemoClock` arranca el 9/11/2026
    a las 10:00 (inscripción, el día anterior a la edición), avanza un segundo
    por lectura para que dos operaciones no compartan hora, y `DemoFixture` lo
    adelanta al final de cada ronda, a la apelación y a la publicación, así las
    horas guardadas son coherentes con la grilla. Cada carga reinicia los dos.
  - **Prueba de repetibilidad**: `DemoFixtureTest` vacía la base con Flyway,
    vuelve a cargar la demo y compara las respuestas de la API (inscripciones,
    rondas, tablas, apelaciones, desgloses) y los ids y horas de cada evento de
    intento. También fija los ids que cita el README y que los tres reglamentos
    usan exactamente los doce tipos de regla.

- **Alternativas descartadas**:
  - Ids con prefijo por tipo (`demo-round-1`, `demo-slot-3`): el puerto
    `IdGenerator` no sabe qué identifica, y darle un tipo, o un generador por
    caso de uso, cambia el núcleo o el cableado solo para la demo.
  - Un reloj fijo, sin avanzar: todas las operaciones compartirían hora, y el
    orden de apelaciones y versiones por fecha quedaría librado al desempate.
  - Volver al reloj del sistema después de cargar: las operaciones nuevas
    quedarían con fechas anteriores a las de la demo (octubre contra noviembre).
  - Empates en otro desafío (Seguidor de línea sin rondas): sumaba rondas y
    capturas para mostrar lo mismo; en Laberinto un solo `GET` muestra el
    descarte y los dos desempates.

- **Deuda**: los ids citados dependen del orden de los pasos de la carga;
  cambiar la demo exige actualizar README y test (el test falla si no). Con la
  app levantada en modo demo, el reloj sigue siendo simulado: las operaciones
  que se hagan desde la web quedan fechadas a continuación de la carga y no con
  la hora real. El empate triple en 717 es un escenario armado para mostrar la
  cadena, no un resultado típico.

- **Clases agregadas**: `DemoClock`, `DemoIds`.
- **Clases modificadas**: `UseCaseConfig`, `DemoConfig`, `DemoFixture`,
  `DemoRulebooks` (javadoc), `DemoFixtureTest`.

### 2.31 Cobertura, espejo de tests e invariantes

- **Problema**: la revisión de tests marcó tres huecos. (a) Muchas invariantes de
  objetos de valor solo tenían probado el `null` o ni eso (`TeamSizeRange`,
  `Season`, `Documentation`, `TimeWindow`, `Placement`, las transiciones de
  `Appeal`...), y otras se probaban con `isInstanceOf` sin mirar el mensaje. (b) No
  se medía cobertura, así que nada impedía que bajara. (c) Varios tests no estaban
  en el paquete de la clase que prueban y algunos se llamaban por el informe que
  los originó (`*ReviewFindingsTest`, "Hallazgo N") en vez de por el
  comportamiento. Además, un panel de jueces vacío puntuaba 0 en silencio, un
  tiempo `NaN` entraba al intento, el umbral de un hito y el mínimo de una regla
  de tiempo no se validaban, y un `EligibilityResult` podía ser "elegible con
  motivos". Los repositorios en memoria devolvían la misma instancia guardada: un
  caso de uso que mutaba sin guardar pasaba su test.

- **Solución Implementada**:
  - **JaCoCo** (`0.8.14`, soporte oficial de Java 25) en el `pom.xml` raíz:
    `prepare-agent` + `report` por módulo (`target/site/jacoco`) y
    `report-aggregate` en `roboleague-api` (`target/site/jacoco-aggregate`), que
    suma lo que los tests de API ejercitan en dominio, aplicación e
    infraestructura (la API declara `roboleague-domain` para que entre en el
    agregado). `check` exige líneas y ramas mínimas en `roboleague-domain` y
    `roboleague-application`, medidas con sus propios tests y fijadas un par de
    puntos por debajo para que la CI bloquee regresiones sin fallar hoy. No se
    excluye ninguna clase: no hay código generado. La CI sube el reporte agregado
    como artefacto `coverage-report`.
  - **Tablas de invariantes**: un `<Paquete>InvariantsTest` por paquete del dominio
    (`tournament`, `scheduling`, `evaluation`, `evaluation.rules`,
    `evaluation.definition`, `evaluation.audit`, `ranking`) con
    `@ParameterizedTest` + `@MethodSource`: cada fila es un caso con nombre, el tipo
    de excepción y el mensaje exacto. `AppealStateTest` es la tabla de
    transiciones de la apelación, como `AttemptStateTest` para el intento.
  - **Invariantes nuevas**, cada una con su fila: `JudgeScores` exige al menos una
    nota (la API responde 400, como el front ya exigía); `TrackPerformance` rechaza
    tiempo no finito; `Milestone.threshold` es finito y no negativo;
    `TimeRuleConfig` rechaza `minPoints > basePoints` (la regla nunca podría bajar
    de su base); `EligibilityResult` no admite "elegible con motivos" ni
    "inelegible sin motivos".
  - **Espejo**: cada test vive en el módulo y paquete de lo que prueba
    (`ranking/appeal/AppealTest`, `tournament/eligibility/...`,
    `evaluation/rules/BasicRulesTest`, `RuleSourcesTest`, `RuleInvariantsTest`,
    `api/config/TransactionalUseCasesTest`, `api/scheduling/RoundControllerTest`).
    Las identidades comparten `support/IdentityContract` y cada paquete prueba las
    suyas. Los nombres dicen el comportamiento (`AttemptScoringTest`,
    `ScoreBreakdownTest`), sin "Hallazgo N".
  - **F1 y F2 por la API y por los casos de uso**: `StandingsControllerTest` muestra
    `considered`/`discarded`/`selectionRule` con mejores 1 de 2;
    `AttemptControllerTest` muestra el ítem "Tope de bonificaciones" con su métrica,
    fórmula y subtotal; `RecalculateStandingsUseCaseTest` y
    `GetAttemptBreakdownUseCaseTest` cubren lo mismo sin HTTP.
  - **Copias en memoria**: los repositorios en memoria de `Attempt`, `Appeal`,
    `Standings` y `Challenge` guardan y devuelven copias hechas con `restore`
    (`Appeal.progress()` expone lo que `restore` necesita). Cuatro tests que
    miraban la instancia vieja pasaron a releer el repositorio; ningún caso de uso
    olvidaba guardar.
  - **Mockito** se quitó: estaba declarado en todos los módulos y no se usaba.

- **Alternativas descartadas**:
  - Un test por clase de valor: decenas de archivos con una o dos filas; la tabla
    por paquete se lee como el catálogo de reglas de ese paquete.
  - Un módulo `roboleague-coverage` solo para el reporte agregado: la API ya
    depende de los demás módulos.
  - Ampliar `TransactionalUseCases` a un paquete de tests para sacar
    `SaveThenFailUseCase` de `com.roboleague.usecase`: cambiaría el cableado de
    producción para probarlo.
  - Un umbral único alto en todos los módulos: infraestructura y API se miden con
    Testcontainers y su cobertura propia depende de qué ejercita cada test de
    integración; se dejan sin mínimo y se ven en el agregado.

- **Deuda**: los mínimos no se ajustan solos; hay que subirlos a mano cuando la
  cobertura crece. `TestValues` sigue duplicado por módulo (un test-jar compartido
  agregaría configuración de build por poco código). Los repositorios en memoria
  de `Edition`, `Team` y `Round` no copian porque esos agregados son inmutables.

## 3. Matriz Comparativa Exhaustiva de Trade-offs

| Decisión Arquitectónica | Pros Clave | Contras y Costos Asociados | Alternativa Considerada y Rechazada |
| :--- | :--- | :--- | :--- |
| **Reificación en Value Objects (`Weight`, `Dimensions`, etc.)** | Erradica la obsesión por primitivos; garantiza invariantes; concentra el comportamiento con sus datos; simplifica firmas. | Mayor cantidad de clases/records; leve sobrecarga de instanciación en memoria. | Usar tipos primitivos sueltos (`double`, `int`). Rechazada por fragilidad e incoherencia de validaciones. |
| **Puntuación Explicable (`ScoreBreakdown` + `ScoreItem`)** | Transparencia total ante apelaciones; cada punto está respaldado por concepto y fórmula auditables. | Mayor sobrecarga de objetos por intento en comparación con un escalar numérico. | Retornar un `double` escalar directo. Rechazada porque impide la justificación reglamentaria y la auditoría. |
| **Reglas de Scoring con `Strategy` + `Composite`** | OCP estricto; permite combinar N reglas homogéneamente; desacopla el desafío del cálculo. | Requiere que `RawMetrics` aloje los campos necesarios para todas las reglas activas. | Codificar el cálculo con `switch-case` en el servicio. Rechazada por violación directa de OCP y SRP. |
| **Reglamento inmutable y versionado por desafío (`Rulebook`)** | Reproducibilidad histórica; una versión nueva no altera resultados puntuados con la anterior. | Hay que publicar una versión nueva para cualquier cambio de reglas. | Política global compartida mutable. Rechazada porque reescribiría o invalidaría resultados históricos. |
| **Append-Only Log con Snapshots y Eventos** | Trazabilidad forense no destructiva; preserva valores originales y cada ajuste con autor y motivo. | El historial crece dentro de la fila del intento (JSONB) y se lee entero en cada carga. | Sobreescritura in-place (`attempt.setScore()`). Rechazada por violar la exigencia explícita de auditoría no destructiva. |
| **Máquina de Estados Polimórfica (`AppealState`)** | Transiciones legalmente seguras; consultas polimórficas sin `if/switch`; cero strings de estado. | Proliferación de clases de estado; necesidad de mappers al momento de persistir en base de datos. | Campo `String` o `Enum` con bifurcaciones condicionales. Rechazada por riesgo de transiciones ilegales y código frágil. |
| **Elegibilidad con `Composite Specification`** | Composabilidad declarativa (`and`, que junta los motivos de ambas reglas); desacoplamiento de criterios; detalle de causales de fallo. | Evaluación sucesiva en memoria; requiere recorrer las listas de integrantes y características del robot. | Validaciones manuales procedurales dentro del caso de uso. Rechazada por violar SRP y no ser reutilizable. |
| **Composition Root en API (configuración Spring)** | Núcleo libre de frameworks; puertos internos y adaptadores seleccionados externamente. | Mantener registros de beans, selección de repositorios y restricciones de proxies transaccionales. | Crear adaptadores tecnológicos dentro del negocio. Rechazada por acoplamiento indebido. |
| **`Challenge` como agregado con su `Rulebook`** | Varios desafíos por evento, cada uno con su reglamento versionado; F1, F2 y F3 viven en el reglamento del desafío. | Captura y apelación tienen que saber de qué desafío es el intento. | Una edición = una prueba (Entrega 1). Rechazada en la Entrega 2: la demo pide tres desafíos en un evento y reglas por desafío. |
| **Recálculo = reordenar snapshots vigentes** | Cumple “reprocesar posiciones después de una corrección”; no reinterpreta la pista; barato y determinista. El intento guarda la versión con que se puntuó (`RulebookReference`, 2.18). | Una versión nueva del reglamento no alcanza a intentos ya abiertos: siguen con la suya. | Re-evaluar todos los `RawMetrics` en cada recálculo de tabla. Rechazada: con reglamentos inmutables el resultado no cambia; `Attempt.recalculateWith`, que lo permitía con cualquier versión, se borró en 2.18. |
| **Tope de bonificaciones en el esquema de puntaje (`BonusLimit`)** | El tope va sobre la suma y queda explicado; ninguna regla de bonificación cambia. | Las bonificaciones se declaran aparte del resto de las reglas, y `Rulebook`/`Challenge.publish` pasaron a recibir un `ScoringScheme` en lugar de una lista de reglas. | Decorator que envuelve las bonificaciones como una regla más. Rechazada: el grupo mezcla fuentes y una regla declara una sola. |
| **Fuente declarada por cada regla (`ScoreRule.source()`)** | El reglamento sabe qué fuentes exige y separa el desglose por fuente sin `if` por tipo de regla. | Cada regla nueva tiene que declarar su fuente; una regla compuesta no puede mezclarlas. | Mapa fuente → reglas en el reglamento. Rechazada: duplica lo que la regla ya sabe. |
| **Esquema de clasificación en el reglamento (`RankingScheme` + `TieBreakCriterion`)** | Mejores N de M y desempate son datos del reglamento y viajan con su versión; cada criterio dice cuándo decidió (`TieBreakDecision`). | Hay que declarar la cadena al publicar el reglamento; un criterio nuevo es una clase y su métrica tiene que estar en `ChallengeScore`. | Cadena de desempate armada en `Main`/config con `Comparator Composition` (`TieBreakerChain`, Entrega 1). Rechazada: dos desafíos no podrían desempatar distinto ni reproducir el criterio de una versión anterior, y un comparador no dice qué criterio decidió (2.4). |
| **Standings versionadas por desafío y categoría** | Una corrección no reescribe una tabla ya vista; publicar solo la última evita el hallazgo 5; el desempate es el del reglamento. | Más tipos y una tabla Postgres aparte; el recálculo no reabre el motor de reglas. | Seguir con `Ranking` por edición/ronda. Rechazada: mezclaba alcance y no versionaba. |
| **Estados del intento con State (`AttemptState`)** | Cada estado acepta solo lo suyo (fuentes, apelaciones, ajustes, descalificación) y cuenta sus apelaciones abiertas. | Cuatro clases y un `AttemptStage` para persistir el estado como dato. | Enum de estado que cada método pisa. Rechazada: rechazar una de dos apelaciones liberaba el intento (2.18). |
| **Puntos exactos (`Points`, `BigDecimal` escala 2)** | El orden de una suma no cambia la tabla; el desempate del reglamento llega a correr. | Conversión en los bordes (`doubleValue()` en DTOs) y parámetros que rechazan más de dos decimales. | `double` con tolerancia. Rechazada: no es transitivo y depende de un umbral arbitrario (2.28). |
| **Persistencia JSONB por agregado** | Cada agregado se lee y se escribe entero; la forma del JSON la fija el adaptador, no el dominio. | Sin integridad relacional dentro del JSON; consultas por hijos con índices GIN. | Tablas hijas y entidades JPA anotadas en el dominio. Rechazada: nadie consulta los hijos por separado y metería JPA en el núcleo (2.14, 2.27). |

---

## 4. Alternativas Descartadas y Decisiones Pospuestas

### Alternativas Descartadas:
1. **Calcular puntajes como un simple número escalar `double`**:
   - *Justificación del descarte*: Un número aislado no permite explicar de dónde surgieron las bonificaciones o deducciones. Ante un reclamo de un participante o un error de arbitraje, resulta imposible auditar qué componente de la fórmula falló.
2. **Actualización in-place destructiva (`attempt.setFinalScore(...)`)**:
   - *Justificación del descarte*: Destruye el rastro de auditoría. Si un árbitro rectifica una penalización tras revisar el video, el sistema debe conservar qué puntaje se había publicado originalmente, quién lo alteró, cuándo y con qué justificación técnica.
3. **Manejo del ciclo de vida de apelaciones mediante flags booleanos o cadenas de texto**:
   - *Justificación del descarte*: Provoca código defensivo plagado de comprobaciones condicionales repetitivas y permite que el sistema ingrese inadvertidamente en estados ilegales (ej. pasar de resuelto a pendiente o aceptar sin haber sido revisado).
4. **Reglas de Scoring acopladas dentro de la entidad `Attempt` o en `Edition`**:
   - *Justificación del descarte*: Viola el principio de Responsabilidad Única (SRP) y Abierto/Cerrado (OCP). La entidad `Attempt` debe modelar la ejecución física y la auditoría de un intento, no el conocimiento de todas las fórmulas matemáticas de todas las categorías de robótica existentes. *Aclaración:* `Challenge` **posee** sus reglamentos publicados; no **implementa** las fórmulas. Las fórmulas viven en `ScoreRule`.
5. **Una edición = una prueba con un solo reglamento (lectura de la Entrega 1)**:
   - *Qué se consideró*: que “desafío” nombrara la forma de puntuar de una edición, y no una prueba dentro de ella.
   - *Qué se eligió*: `Challenge` como agregado propio con su `Rulebook` versionado (2.8).
   - *Justificación del descarte*: la Entrega 2 pide tres desafíos en el mismo evento y reglas por desafío (N de M, tope de bonificaciones). La Entrega 1 ya anotaba como contra que esa lectura no cubría “una jornada con varias pruebas”; la consigna nueva es justamente ese caso.
6. **Lista de desafíos dentro de `Edition`**:
   - *Justificación del descarte*: haría crecer a `Edition` con cada desafío y sus versiones de reglamento, y obligaría a rondas y tablas a pasar por la edición para llegar a su desafío. Como agregado aparte, cada uno se guarda y se referencia por id.
7. **Re-evaluar todos los intentos con `ScoreRule` en cada recálculo de ranking**:
   - *Qué se consideró*: que “recalcular con la versión de reglas correspondiente” significara volver a correr el motor sobre cada `RawMetrics` al reconstruir la tabla (`Attempt.recalculateWith` en el use case).
   - *Qué se eligió*: la versión del reglamento se aplica al **nacer** el puntaje (captura, ajuste o apelación aceptada), siempre la que el intento guardó al abrirse (2.18). `RecalculateStandingsUseCase` reprocesa **posiciones** a partir del último snapshot y agrega una versión de la tabla (2.26). Es la lectura de la tabla de la consigna.
   - *Justificación del descarte*: con reglamentos publicados inmutables, re-evaluar no cambia el desglose y encarece el recálculo. `Attempt.recalculateWith`, que lo permitía con cualquier versión, se eliminó en 2.18: si el negocio pidiera re-aplicar otro reglamento habría que modelarlo como una corrección auditada.
8. **Mejores N de M como una `ScoreRule` más**:
   - *Justificación del descarte*: una regla evalúa las métricas de un intento; N de M elige entre puntajes ya calculados de varias rondas. Meterlo en el motor de reglas obligaría a que una regla conozca otros intentos.
9. **Enum de criterios de desempate con comparadores (como `TieBreakerChain.StandardCriterion` de la Entrega 1)**:
   - *Justificación del descarte*: no dice qué criterio decidió y agregar uno obliga a abrir el enum (OCP). Cada criterio es una clase con nombre.

### Evolución de decisiones técnicas inicialmente pospuestas:
1. **Framework de Persistencia Real (JPA / Hibernate / Spring Data)**:
   - *Entrega 1*: Se difirió la persistencia real porque la consigna se centraba en el módulo de dominio.
   - *Estado actual*: infrastructure incorpora Spring Data JPA, Hibernate, Postgres y migraciones Flyway para los siete agregados (`Edition`, `Team`, `Round`, `Challenge`, `Attempt`, `Appeal` y `Standings`). Las entidades JPA y mappers son propios del adaptador; domain y application permanecen libres de anotaciones de persistencia. Flyway administra el esquema y Hibernate lo valida.
2. **Contenedor de Inyección de Dependencias Automatizado (Spring / Guice / CDI)**:
   - *Entrega 1*: Se utilizó ensamblado manual en `Main` y fixtures de test.
   - *Estado actual*: Spring Boot ensambla la aplicación desde API; `UseCaseConfig` registra casos de uso y servicios, y `TransactionalUseCases` aplica las transacciones. El núcleo conserva inyección por constructor y Java sin anotaciones Spring. La independencia del dominio se mantiene aunque el ensamblado externo use un framework.
3. **Motor de Reglas Externo con DSL (Drools / Rete)**:
   - *Decisión*: Resolver el scoring mediante composición de objetos Java puros (`ScoreRule`).
   - *Justificación*: Strategy + Composite permite componer y probar reglas Java sin un motor externo. Las definiciones configurables se validan y reconstruyen mediante `RuleCatalog`; eso incluye procesamiento en ejecución. No se presupone ausencia de parsing ni superioridad de rendimiento sin mediciones.

---

## 5. Estrategia de Verificación y Cobertura de Pruebas

Cada test vive en `src/test/java` del módulo y en el paquete de la clase que prueba (2.31), y todos se llaman `*Test`. `mvn -B verify` corre los cuatro niveles; la CI (`.github/workflows/ci.yml`) lo ejecuta en cada PR y push a `main`, junto con los tests de módulos JS del front (`node --test roboleague-api/src/test/frontend/*.test.mjs`).

- **Pruebas Unitarias de Scoring y Auditoría** (`roboleague-domain`, `evaluation`):
  - `rules/BasicRulesTest`: Evalúa el comportamiento de cada regla elemental (`TimeBasedRule`, `ObjectivesRule`, `AllObjectivesBonusRule`, `PenaltyRule`, `JudgeSubjectiveRule`), verificando cada ítem del desglose. `RulebookSourcesTest` agrega su consolidación en un `Rulebook`.
  - `ScoreBreakdownTest`: el total de un desglose es la suma de sus ítems, piso en cero incluido. `rules/RuleInvariantsTest`: tabla parametrizada con cada parámetro inválido de cada regla (tipo de excepción y mensaje) y la inmutabilidad de la regla compuesta.
  - `RuleCatalogTest`: Verifica, con casos parametrizados, que cada tipo de regla, estrategia y criterio se describe y se reconstruye igual y puntúa lo mismo, y que una definición inválida (tipo desconocido, parámetro o métrica faltante, parámetro inválido) se rechaza nombrando el problema. Suma la ida y vuelta de las métricas declaradas y los rechazos por rango inválido, métrica repetida, no declarada o de otra fuente, por sección equivocada o por parámetros desconocidos.
  - `BonusCapTest` (F2): Verifica, con casos parametrizados, que el tope recorta solo lo que la suma de bonificaciones supera, que se aplica sobre el conjunto y no sobre cada una, que la explicación muestra lo obtenido, el tope y el recorte, y que agregar el tope no cambia lo que da cada regla de bonificación.
  - `MetricDefinitionTest` y `MeasurementCheckTest` (F3): con casos parametrizados, que una medición se acepta solo entera si es un conteo y dentro de su rango, y que revisar lo que manda una fuente junta todos los problemas (faltante, mal escrita, de otra fuente, fuera de rango) sin exigir las métricas de la otra fuente.
  - `rules/ScoreRuleSectionsTest`: con casos parametrizados por regla y carrera (perfecta, media, mala), que una regla base o una bonificación nunca resta y una deducción nunca suma.
  - `rules/MeasuredRulesTest`, `rules/RuleSourcesTest` y `RulebookSourcesTest` (F3): las reglas medidas (faltas con franquicia, precisión, víctimas, hito), que una medición faltante no se convierte en cero, la fuente y las métricas que declara cada regla, las fuentes que exige un reglamento y que, sin tope, las contribuciones por fuente suman el total.
  - `scheme/BestNOfMTest` y `scheme/RankingSchemeTest` (F1): qué rondas cuentan con mejores N de M y cuáles se descartan, que el primer criterio que separa a dos equipos dé su nombre, el empate en todos los criterios, que el orden declarado cambie al ganador y que un Seguidor de línea real desempate por menor descuento.
  - `PointsTest`: escala fija de dos decimales, igualdad sin importar cómo se construyó, sumas exactas e independientes del orden, redondeo HALF_EVEN y rechazo de valores no finitos.
  - `AttemptSourcesTest` (F3): con una fuente el intento espera la otra sin puntaje; con las dos se puntúa igual en cualquier orden; la misma fuente dos veces se rechaza y conserva la primera; mediciones rechazadas no cambian nada; cada fuente explica su parte.
  - `AttemptScoringTest`: ningún método público recibe un puntaje; el intento y cada revisión conocen su versión de reglamento y rechazan otra; una apelación aceptada puntúa las métricas corregidas; un intento sin puntuar o descalificado no tiene puntaje computable; con dos apelaciones abiertas, rechazar una no lo libera.
  - `AttemptStateTest` (tabla de transiciones de cada estado del intento), `AttemptRestoreTest` (rehidratación sin repetir transiciones) y `AttemptAuditTrailTest` (snapshots inmutables y correlativos, revisión original intacta, eventos, descalificación auditada y restauración tras una apelación rechazada).
  - `tournament/ChallengeTest`: cada publicación del reglamento crea la versión siguiente, una versión nueva no cambia cómo puntúa la anterior y se puede pedir una versión exacta.
- **Tablas de invariantes e identidades** (2.31): `TournamentInvariantsTest`, `SchedulingInvariantsTest`, `EvaluationInvariantsTest`, `rules/RuleInvariantsTest`, `definition/DefinitionInvariantsTest`, `audit/AuditInvariantsTest` y `RankingInvariantsTest` son tablas `@ParameterizedTest` con el tipo y el mensaje de cada rechazo. Las identidades comparten `support/IdentityContract`: `TournamentIdentitiesTest`, `SchedulingIdentitiesTest`, `AttemptIdTest`, `appeal/AppealIdTest` y `ActorIdTest`. `RobotPhysicalValuesTest` rechaza peso y dimensiones no finitos.
- **Pruebas Unitarias de Dominio y Flujos de Estado**:
  - `ranking/appeal/AppealTest` y `ranking/appeal/AppealStateTest`: el ciclo completo de una apelación y la tabla de transiciones de cada estado (las aceptadas y las rechazadas con su excepción y mensaje).
  - `StandingsTableTest` (F1): el orden y la explicación de cada puesto con la cadena del reglamento, incluyendo empates, desempates y rondas descartadas.
  - `StandingsTest` y `PublicationCheckTest`: versionado append-only, bloqueos de publicación y conteo de apelaciones y turnos abiertos (un turno cancelado no bloquea).
  - `eligibility/EligibilitySpecificationTest` y `eligibility/EligibilityResultTest`: las especificaciones compuestas de edad, integrantes, robot y documentación, y que un resultado no sea "elegible con motivos" ni "inelegible sin motivos". `RegistrationModelTest`, `ExplicitCalendarTest` y `EditionTest`: inmutabilidad de candidatos, identidad edición/equipo, calendario explícito y categorías únicas.
  - `scheduling/RoundTest` y `scheduling/RoundSchedulerServiceTest`: ciclo de vida de ronda y turnos, cancelación con autor y motivo, fechas de la edición, solapamientos con intervalos semicerrados, programación en paralelo con pausa de pista, un juez por turno, recursos comparados por id y pistas inactivas excluidas.
- **Pruebas de Casos de Uso** (`roboleague-application`, con los repositorios en memoria, que guardan copias):
  - `RegisterTeamUseCaseTest`, `UpdateTeamUseCaseTest` y `UpdateRegistrationUseCaseTest`: admisión y rechazo fundamentado, validación de todas las inscripciones antes de escribir y cambios conjuntos de equipo y categoría sin cambios parciales.
  - `ScheduleRoundUseCaseTest` y `RoundSchedulingIntegrationTest`: programación correcta, rechazo de una ronda fuera de las fechas de la edición, revalidación del equipo canónico antes de generar ids, orden por momento de inscripción y TeamId, ocupaciones de rondas guardadas y scope duplicado sin escrituras.
  - `RoundLifecycleUseCaseTest`: ciclo de vida de ronda y turnos; cancelar un turno al que el equipo no se presentó destraba la publicación; un turno cancelado no toma resultados y uno con resultados no se cancela.
  - `ReceiveResultUseCaseTest` (F3): el intento pertenece a la ronda y al equipo del turno, el desafío mixto espera al panel, una segunda captura se rechaza, un juez no asignado o mediciones rechazadas no guardan nada y un intento pendiente se puntúa con su versión aunque se publique otra.
  - `CorrectAttemptUseCaseTest`: un ajuste de faltas agrega una revisión y recalcula la tabla; un intento descalificado deja de contar; solo un juez asignado descalifica y una sola vez.
  - `GetAttemptBreakdownUseCaseTest` (F2) y `RecalculateStandingsUseCaseTest` (F1): el desglose muestra el recorte del tope como ítem con su fórmula; mejores 1 de 2 conserva la mejor ronda y explica la descartada; cada recálculo agrega una versión.
  - `StandingsUseCasesTest`: recálculo por categoría, versiones nuevas, bloqueo por turnos sin resultado o resultados posteriores, y el listado de apelaciones. `ResolveAppealUseCaseTest`: rechazar conserva el puntaje original y libera el intento. `ConfigureChallengesUseCaseTest`: crear edición, agregar desafío, publicar versión y sus rechazos.
  - `AppealAndRecalculateIntegrationTest`: **prueba de integración end-to-end** del ciclo completo: Registro de equipos -> Planificación de ronda -> Captura inicial de intentos -> Cálculo de standings provisionales -> Presentación de apelación -> Bloqueo de publicación -> Revisión -> Aceptación con métricas corregidas -> Auditoría del intento -> Recálculo automático como versión nueva con inversión de posiciones -> Publicación de la última versión.
- **Pruebas de Repositorio** (`roboleague-infrastructure`, `@DataJpaTest` contra Postgres en Testcontainers): un `Jpa*RepositoryTest` por agregado (`Edition`, `Team`, `Round`, `Challenge`, `Attempt`, `Appeal`, `Standings`) verifica la ida y vuelta completa, las búsquedas y el rechazo de escrituras obsoletas con `ConcurrentModificationConflict`; `JpaChallengeRepositoryTest` comprueba que cada versión restaurada puntúa igual que antes (tope incluido) y `JpaAttemptRepositoryTest` que un dato irreconstruible es `CorruptDataException`. `InMemoryChallengeRepositoryTest` e `InMemoryRoundRepositoryTest` fijan el mismo contrato de orden y colisiones en memoria; `SystemClockTest`, la zona configurada.
- **Pruebas de API** (`roboleague-api`, `@SpringBootTest` + MockMvc sobre la app real, subclases de `ApiTest`):
  - `challenge/ChallengeControllerTest` y `edition/EditionControllerTest`: 201, 400, 404, 409 y 422 de la configuración; un dato obligatorio faltante es 400 y no 500.
  - `registration/RegistrationControllerTest` y `scheduling/RoundControllerTest`: inscripción, actualización conjunta y programación solo con contratos HTTP, sin rondas ni equipos parciales ante un rechazo. `scheduling/RoundLifecycleControllerTest`: ciclo de vida y cancelación con autor y motivo.
  - `attempt/AttemptControllerTest` (F2, F3): captura de cada fuente, desglose pendiente y por fuente, el ítem "Tope de bonificaciones" con su métrica, fórmula y subtotal, y los 404/409/422 de la captura. `attempt/AttemptCorrectionControllerTest`: ajuste de faltas y descalificación con la versión nueva de la tabla.
  - `appeal/AppealControllerTest` y `standings/StandingsControllerTest` (F1): ciclo de apelaciones, recálculo, publicación bloqueada (409 con motivos) y `considered`/`discarded`/`selectionRule` con mejores 1 de 2.
  - `ApiExceptionHandlerTest`: cada causa con su estado y las fallas técnicas sin detalles internos. `config/TransactionalUseCasesTest`: un caso de uso que falla después de guardar no deja nada guardado. `demo/DemoFixtureTest`: los tres desafíos, los doce tipos de regla, descartes, desempates por segundo y tercer criterio, el turno cancelado y que cargar la demo dos veces da los mismos ids, horas y totales. `WebFrontendTest`: el front se sirve en el mismo puerto.
- **Cobertura**: JaCoCo deja un reporte por módulo en `<módulo>/target/site/jacoco` y uno agregado en `roboleague-api/target/site/jacoco-aggregate`, que la CI publica como artefacto `coverage-report`. `verify` falla si las líneas o ramas de `roboleague-domain` o `roboleague-application` bajan del mínimo de su `pom.xml` (2.31).

---

## 6. Resumen por funcionalidad nueva (F1, F2, F3)

Lista consolidada con los nombres actuales del código. El detalle y la historia de cada decisión están en las secciones citadas. "Agregada" incluye las clases que nacieron para consumir la funcionalidad aunque llegaran en otra sección.

### F1: mejores N de M rondas (2.10, 2.17, 2.26, 2.28, 2.30)

**Clases agregadas**
- Reglamento (`evaluation.scheme`): `RankingScheme`, `RoundSelection` con `BestNOfM` y `AllRounds`, `RoundScore`, `ChallengeScore` (rondas consideradas, descartadas y la regla aplicada), `TieBreakCriterion` con `HigherTotal`, `LowerTime`, `LowerDeductions` y `HigherJudgeScore`, `TieBreakDecision` (`DecidedBy` | `Tied`) y `MissingGoesLast`.
- Tabla (`ranking`, 2.26): `StandingsTable`, `TeamAttempts` (mejor intento computable por ronda), `TeamRounds`, `RoundResult`, `StandingsEntry`, `Placement`, `CategoryResults`; caso de uso `RecalculateStandingsUseCase`.
- API: `StandingsVersionDto.EntryDto` (`selectionRule`, `considered`, `discarded`, `explanation`).

**Clases modificadas**
- `Rulebook` (tercer componente `rankingScheme()`), `Challenge` (`Draft.publish` y `publish` reciben el esquema), `RuleCatalog` (tipos `best-n-of-m`/`all-rounds` y códigos de criterio), `RulebookDefinition` (`Ranking`), `RulebookBody`, `RulebookDto` y `ChallengeMapper` (el bloque `ranking` del JSON).
- `DemoFixture`/`DemoRulebooks` (Laberinto: mejores 3 de 5 con descartes y desempates visibles, 2.30).

**Refactorizaciones realizadas**
- `TieBreakerChain` (composición de `Comparator<TeamScore>`), `RankingCalculatorService`, `TeamScore` y `Ranking` se reemplazaron por `RankingScheme` + `StandingsTable` (2.4, 2.26).
- `FewerPenalties` pasó a `LowerDeductions`, que compara los puntos descontados por cualquier deducción (2.17).
- Totales y comparaciones de `RoundScore`, `ChallengeScore`, `TeamRounds` y `StandingsEntry` pasaron de `double` a `Points` (2.28).
- Ninguna `ScoreRule` cambió: N de M trabaja sobre puntajes ya calculados.

**Deuda técnica que se decidió no resolver**
- ~~M no se valida al programar.~~ Resuelta: `ScheduleRoundUseCase` consulta `RoundSelection.allowsRound` y rechaza la ronda M+1 con 422 (2.10).
- ~~Una versión nueva podía bajar M por debajo de las rondas ya programadas.~~ Resuelta: `PublishRulebookUseCase` la rechaza con 422 (2.10).
- Un criterio de desempate que mire otra métrica exige exponerla primero en `ChallengeScore` (2.10).
- Los empates entre rondas con el mismo puntaje se resuelven por la más temprana, sin criterio configurable.

### F2: tope global de bonificaciones (2.12, 2.17, 2.28)

**Clases agregadas**
- `ScoringScheme` (métricas, reglas y límite), `BonusLimit` con `CappedAt` y `Unlimited`, `ScoreRules` (2.16).
- Secciones tipadas (2.17): `BaseRule`, `BonusRule`, `DeductionRule`, `RuleSection`, y las reglas separadas `AllObjectivesBonusRule` y `AbandonedVictimsRule`; `RulebookDefinition.Bonuses`.

**Clases modificadas**
- `Rulebook` (recibe un `ScoringScheme`), `Challenge` (`publish` con el esquema), `ScoreRule.RuleEvaluation` (`concat`), `ScoreBreakdown` (base, bonificaciones con su tope y deducciones; total derivado), `ObjectivesRule`, `VictimsRule`, `CompositeScoreRule` (solo reglas base), `RuleCatalog` (registro por sección), `RulebookBody`, `RulebookDto`, `ChallengeMapper` y `DemoRulebooks` (topes 40 / 30 / 30). El ítem "Tope de bonificaciones" (lo obtenido, el tope y el recorte) lo agrega `CappedAt` al desglose y la API lo muestra sin cambios en `BreakdownDto.items`.

**Refactorizaciones realizadas**
- El bono por completar todos los objetivos salió de `ObjectiveBonusRule` (renombrada `ObjectivesRule`) a la bonificación `AllObjectivesBonusRule`, para que el tope cubra todas las bonificaciones (2.17).
- `VictimsRule` dejó de restar: las abandonadas son la deducción `AbandonedVictimsRule` (2.17).
- `ScoringScheme` agrupó sus listas en `ScoreRules` y el desglose pasó a guardar secciones y derivar el total (2.16, 2.17).
- Ninguna regla de bonificación cambió cómo puntúa.

**Deuda técnica que se decidió no resolver**
- El bono por segundo bajo el objetivo de la regla de tiempo es parte de la base y no entra al tope (2.17).
- `all-objectives` es un hito sobre un campo fijo: se unifica con `milestone` cuando objetivos y faltas sean métricas declaradas (2.16, 2.17).
- Los reglamentos guardados antes de las secciones no se reconstruyen (`CorruptDataException`); solo había datos de la demo (2.17).
- La escala de `Points` es global (centésimas) y los parámetros siguen viajando como `double` (2.28).

### F3: desafío mixto con dos fuentes (2.11, 2.16, 2.18)

**Clases agregadas**
- Reglamento: `ResultSource`, `Metric`, `SourceContribution`, `MetricDefinition`, `MeasurementUnit`, `ValueRange`, `MetricSheet`, `MeasurementCheck` (`Accepted` | `Rejected`), `MetricDeclaration`, y las reglas medidas `PrecisionRule`, `VictimsRule`, `MilestoneBonusRule`, `CountedFaultRule` con `FaultTariff`, `VictimTariff` y `Milestone`.
- Intento: `SourceReport` (sellado) con `Measurements` y `JudgeScores`, `SourceDelivery`, `SourceReceivedEvent`, `AttemptState` con `WaitingAttemptState`, `SettledAttemptState`, `UnderAppealAttemptState` y `DisqualifiedAttemptState`, `AttemptStage`, `AttemptProgress`, `AttemptId`, `RulebookReference`, `AppealRevision`, `AuditNote`, `AuditTrail`.
- Aplicación: `ReceiveResultUseCase`, `ReceiveResultCommand`, `Reception` (`Received` | `Rejected`), `GetAttemptBreakdownUseCase`, `AttemptBreakdown`.
- API: `AttemptController` (`PUT /attempts/{id}/measurements`, `PUT /attempts/{id}/judge-scores`, `GET /attempts/{id}/breakdown`), `MeasurementsBody`, `JudgeScoresBody`, `AttemptDto`, `BreakdownDto` (`awaiting`, `bySource`).
- Persistencia: `AttemptJpaEntity`, `AttemptMapper`, `JpaAttemptRepository` (con `@Version`), `SpringDataAttempts`, `MetricsJson`.

**Clases modificadas**
- `ScoreRule` (`source()`, `metrics()`), las reglas de la Entrega 1 (`source()`), `CompositeScoreRule` (una sola fuente), `Rulebook` (`requiredSources`, `contributions`, `check`), `RawMetrics` (`measurement`, `withPenalties`), `EvaluationFeedback`.
- `Attempt` (`receive`, se puntúa solo con la versión que guardó), `AttemptIdentity`, `EvaluationSnapshot`, `AttemptScoreSnapshot`, `AttemptEvent` (sellado), `ResultRegisteredEvent`, `ScoreAdjustedEvent`, `Round`/`Slot` (`slot(id)`, `isJudgedBy`), `ScheduleRoundUseCase`, `FileAppealUseCase`, `ResolveAppealUseCase`, `AppealMapper`, `UseCaseConfig`, `DemoFixture` (Rescate con dos fuentes).

**Refactorizaciones realizadas**
- `Rulebook` dejó de usar un composite interno: guarda listas que pueden mezclar fuentes y combina evaluaciones con `RuleEvaluation.combining`/`concat` (2.11).
- `CaptureAttemptResultUseCase` (que recibía un desglose armado) se reemplazó por `ReceiveResultUseCase` y `Attempt.receive`; el intento se puntúa solo (2.18).
- El estado del intento pasó de enum a State; el id del intento se deriva del turno; `Attempt.recalculateWith` y `SlotReference` se eliminaron (2.18).

**Deuda técnica que se decidió no resolver**
- Tiempo, objetivos, faltas, consumo y notas de jueces siguen siendo campos fijos y no métricas declaradas; por eso `PenaltyRule` y `CountedFaultRule` sin franquicia calculan lo mismo (2.11, 2.16).
- Del panel solo se valida al juez que carga, no a cada juez que puntúa; no se valida el horario del turno y un turno `SCHEDULED` acepta resultados (2.18, 2.29). Validar a cada juez exige que la ronda conozca su panel: hoy guarda solo el juez asignado a cada turno, y los jueces nombrados al programar que no recibieron turnos se pierden.
- Las notas de los jueces no tienen escala ni máximo: `JudgeScores` solo exige que sean finitas y no negativas, así que una nota de 9999 se acepta y se multiplica por `weightMultiplier`. La salida sería declarar la escala en la regla `judges` (un `maxScore` opcional) y revisarla al capturar el panel y al corregirlo en una apelación.
- `ScoreRule.source()` admite una sola fuente: una regla que combine sensores y panel no se puede expresar (2.11).
- Una métrica declarada que ninguna regla lee igual se exige en la captura, y su rango no se cruza con los parámetros de la regla (2.16).
- Dos fuentes simultáneas se resuelven con bloqueo optimista: la segunda recibe 409 y el cliente tiene que reintentar; no hay reintento automático (2.18).

---

## 7. Patrones y técnicas que se decidió no aplicar

| Patrón/técnica | Dónde habría aplicado | Por qué no | Consecuencia asumida |
| --- | --- | --- | --- |
| Event sourcing | `Attempt` (revisiones y eventos) y `Standings` (versiones) | Las revisiones guardadas ya son el registro de auditoría; reconstruir desde eventos exige versionar y reproducir eventos sin un beneficio que la consigna pida (2.18) | Los eventos documentan, pero no son la fuente de verdad: si snapshot y evento difirieran manda el snapshot. Cada fila guarda su historial completo en JSONB y crece con cada corrección |
| CQRS (modelo de lectura separado) | Tablas de posiciones y desgloses | La lectura es barata y del mismo agregado; un segundo modelo agregaría sincronización | La tabla se recalcula de forma sincrónica dentro del caso de uso que corrige (apelación, ajuste) y una captura nueva no la actualiza: queda `pending.outdated` hasta recalcular a mano |
| Publicación de eventos de dominio (bus, listeners) | Recalcular la tabla al cambiar un puntaje | Un caso de uso explícito es más simple de seguir y corre en la misma transacción | Cada caso de uso que cambia un puntaje tiene que llamar al recálculo; olvidarlo deja la tabla vieja (lo detecta el bloqueo de publicación por resultados posteriores) |
| Interfaz por caso de uso (puerto de entrada) | `roboleague-application` | Hay una sola implementación por caso de uso y los controllers no necesitan variarla | Controllers y `UseCaseConfig` dependen de clases concretas; las pruebas de API ejercitan la pila real en vez de dobles del caso de uso |
| Bounded contexts separados o microservicios | `tournament`, `scheduling`, `evaluation`, `ranking` | Comparten tipos y una misma transacción; no hay equipos ni ciclos de despliegue distintos | Un solo despliegue; un cambio en un tipo compartido se propaga a todas las áreas; quedan ciclos de paquetes (`evaluation` ↔ `evaluation.rules`/`definition`, 2.13) |
| Anotaciones de Spring, JPA o Jackson en domain/application | Agregados, reglas y casos de uso | Mantener el núcleo sin frameworks (regla #11) y la API/persistencia como adaptadores | Un mapper y una entidad JPA por agregado, `RulebookBody` y `ChallengeMapper` duplican la traducción JSON ↔ definición, y las transacciones dependen de proxies de clase de `TransactionalUseCases` (un caso de uso `final` o fuera del paquete quedaría sin transacción) |
| Agregados mapeados por ORM (tablas hijas, `@OneToMany`) | Reglamentos, inscripciones, integrantes, slots, revisiones | Cada agregado se lee y se escribe entero y nadie consulta los hijos por separado; se eligió JSONB (2.14, 2.27) | Sin integridad relacional dentro del JSON: `findByTeamId`/`findBySlotId` usan contención con índices GIN y la unicidad de `SlotId` la comprueba el adaptador. Cambiar la forma del JSON exige migrar o aceptar `CorruptDataException` al leer datos viejos |
| Exclusión entre programaciones concurrentes (bloqueo pesimista o serialización) | `ScheduleRoundUseCase` sobre rondas distintas | Cada ronda tiene su versión; serializar todas las programaciones complica el adaptador sin un caso real de concurrencia | Dos programaciones simultáneas de rondas distintas pueden asignar la misma pista o juez a la misma hora (2.23, 2.27) |
| Autenticación y autorización | Toda la API y el front | Fuera del alcance de la consigna | `actorId`, `judgeId`, `reviewerId` y `publishedBy` se confían al pedido: cualquiera puede descalificar, resolver apelaciones o publicar, y el autor auditado no está verificado (2.29) |
| Comparator Composition | Orden de la tabla (`TieBreakerChain`, Entrega 1) | Un comparador devuelve un número y no qué criterio decidió; la cadena vivía en código y no en el reglamento (2.4) | Cada criterio es una clase con `compare` y `RankingScheme.decide` devuelve `TieBreakDecision`; agregar un criterio es una clase más su entrada en `RuleCatalog` |
| Enum de criterios de desempate (`TieBreakerChain.StandardCriterion`) | Cadena de desempate | Agregar un criterio obliga a abrir el enum (OCP) y no explica cuál decidió (capítulo 4, ítem 9) | Más clases (una por criterio) y un `code()` estable por criterio para la API |
| Decorator sobre `ScoreRule` para el tope | F2 | Las bonificaciones mezclan fuentes y una regla declara una sola (2.12) | Las bonificaciones se declaran en su propia sección y el tope vive en `ScoringScheme`; el recorte no pertenece a ninguna fuente en el desglose |
| Decorator sobre `RoundSelection` | F1 | No hay una variación que lo pida (2.10) | Combinar selecciones (por ejemplo "mejores N sin la última") exige una clase nueva |
| Visitor | Describir reglas, clasificarlas por sección, sumar fuentes (2.13, 2.17, 2.18) | Cada regla ya sabe describirse (`definition()`, `metrics()`, `source()`) y cada fuente suma lo suyo (`addTo`) | Una operación nueva sobre todas las reglas obliga a tocar las doce clases |
| `ScoreRule` sellada | Catálogo de reglas | Cerraría el conjunto de reglas, contra OCP (2.17) | El compilador no puede verificar un `switch` exhaustivo sobre reglas; el único lugar que mapea tipo → clase es `RuleCatalog` |
| Builder para reglamentos | Armado de `Rulebook`/`ScoringScheme` | Los records alcanzan (2.13) | Construir un reglamento en tests es verboso; se compensa con `ScoringScheme.withoutBonuses` y `TestValues` |
| Specification para métricas | `MetricSheet`/`MeasurementCheck` | No hay combinaciones `and`/`or`: cada métrica se revisa sola (2.16) | Una regla de captura que combine métricas (por ejemplo "rescatadas ≤ total") no tiene dónde expresarse |
| State para `Round`/`Slot` | Ciclo de vida de rondas y turnos | Los estados no cambian el comportamiento como en `Attempt`/`Appeal`; alcanzan transiciones explícitas en `Round` (2.23) | Las transiciones son condiciones sobre un enum dentro de `Round`; un estado nuevo exige tocar sus métodos |
| `@Transactional` en los casos de uso | `roboleague-application` | Metería Spring en el núcleo (2.19) | Las transacciones dependen de que el bean esté en el paquete exacto `com.roboleague.usecase`; la prueba de rollback necesita `SaveThenFailUseCase` en ese paquete de tests |
| Mocks (Mockito) | Tests de casos de uso | Los adaptadores en memoria prueban comportamiento y no interacciones; Mockito estaba declarado y sin uso (2.31) | Los adaptadores en memoria se mantienen junto con cada puerto y copian los agregados mutables; una falla técnica a mitad de transacción solo se prueba a nivel API |
| Motor de reglas externo con DSL (Drools) | Puntuación | Strategy + Composite y `RuleCatalog` alcanzan y se prueban con Java (capítulo 4) | Cada tipo de regla nuevo requiere código Java y un despliegue; un organizador no puede inventar una fórmula sin programar |
