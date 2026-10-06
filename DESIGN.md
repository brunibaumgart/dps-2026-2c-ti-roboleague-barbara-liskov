# Documento de Decisiones de Diseño (DESIGN.md) - RoboLeague

**Trabajo Integrador - Entrega 1: Módulo de Dominio**  
**Plataforma de Competencias de Robótica y Desafíos Técnicos**

---

## 1. Arquitectura de Dominio y Contextos Delimitados (Bounded Contexts)

El dominio de **RoboLeague** se estructura en cuatro submódulos conceptuales altamente cohesivos y débilmente acoplados, reflejando el flujo natural de una competencia técnica:

```text
roboleague-domain/
├── tournament/          # Eventos, Ediciones, Categorías, Equipos, Elegibilidad
├── scheduling/          # Rondas, Turnos (Slots), Pistas, Jueces
├── evaluation/          # Intentos, Métricas capturadas, Reglas de Scoring, Auditoría
└── ranking/             # Criterios de ordenamiento, Desempates, Publicación, Apelaciones
```

### Responsabilidades por Contexto:
1. **`tournament`**: Modela el ciclo organizativo: definición de temporadas, torneos y ediciones cronológicas; categorización técnica con restricciones físicas y etarias; registro de equipos, participantes y especificaciones de robots; y evaluación compuesta de elegibilidad.
2. **`scheduling`**: Modela la logística operativa de campo: gestión de pistas o arenas de prueba, designación y perfiles de jueces evaluadores, planificación de rondas y asignación determinista de turnos (slots) con ventanas temporales y pausas intermedias.
3. **`evaluation`**: Corazón computacional del sistema. Modela los intentos en pista (`Attempt`), la captura estructurada de métricas observadas (`RawMetrics`), el motor de puntuación explicable y versionado (`Rulebook`, `ScoreRule`, `ScoreBreakdown`), y el rastro de auditoría append-only con snapshots inmutables y eventos de dominio.
4. **`ranking`**: Consolida los puntajes agregados por equipo (`TeamScore`), resuelve empates jerárquicamente mediante cadenas desacopladas (`TieBreakerChain`), administra la publicación oficial o provisional de tablas (`Ranking`), y gobierna el ciclo de apelaciones (`Appeal`) mediante una máquina de estados polimórfica.

**Cardinalidad del evento:** una `Edition` es el evento y tiene varios `Challenge` (Laberinto, Seguidor de línea, Rescate). Cada desafío publica su propio reglamento versionado (`Rulebook`). `Category` es elegibilidad, no un desafío. Ver 2.8.

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
  - *Métricas y Puntuación*: `TimeTargets`, `TimeAdjustments`, `TimeRuleConfig`, `ObjectiveRuleConfig`, `TrackPerformance`, `EvaluationFeedback` y `EvaluationDetails`.
  - *Clasificación*: `TieStatus`, `TeamIdentity`, `CompetitionContext`, `TeamEntryHeader`, `PerformanceSummary` y `RankingScope`.
  - *Auditoría y Reclamos*: `SnapshotIdentity`, `AuditAuthor`, `SnapshotMetadata`, `EvaluationSnapshot`, `AppealTarget` y `AppealClaim`.

  La lógica de dominio se ubicó estrictamente donde reside el dato:
  - `Dimensions.fitsWithin(Dimensions)` determina el encaje físico espacial.
  - `RobotLimits.allows(RobotSpecification)` y `checkViolations(spec)` verifican el cumplimiento de masa y volumen.
  - `AgeRange.contains(int age)` y `TeamSizeRange.allows(int count)` validan las reglas de admisión.
  - `PerformanceSummary.isTiedWith(PerformanceSummary)` y `TeamScore.isTiedWith(TeamScore)` encapsulan la determinación de igualdad en métricas.

- **Pros**:
  - **Invariantes Garantizadas en el Origen**: Un objeto no puede existir en estado inconsistente (ej. peso negativo, dimensiones invertidas, rangos con mínimo mayor que máximo).
  - **Auto-documentación y Expresividad**: El código habla el lenguaje ubicuo de la robótica en lugar de tipos genéricos (`double`, `String`, `int`).
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
  - **El desglose explica el total**: `ScoreBreakdown` rechaza un total que no sea la suma de sus ítems (tolerancia de medio centavo, para totales que vuelven redondeados de la base o la API). El piso en cero ya no se aplica en silencio: aparece como ítem `Piso en cero` con fórmula `max(0, suma)`. Se descartó un constructor privado porque un record no admite constructor canónico privado; validar en el constructor compacto da la misma garantía.
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
  - Adicionalmente, el aggregate emite y almacena eventos de dominio inmutables (`ResultRegisteredEvent`, `ScoreAdjustedEvent`, `PenaltyAppliedEvent`, `AppealAcceptedEvent`).
  - La entidad mantiene acceso directo a la primera revisión original (`getOriginalSnapshot()`) y a la última vigente (`getLatestSnapshot()`).

- **Pros**:
  - **Auditoría Forense Completa**: Es posible reconstruir el estado exacto del intento en cualquier momento de su ciclo de vida.
  - **Imposibilidad de Manipulación Silenciosa**: Cualquier corrección requiere autor, motivo y genera una nueva entrada inmutable.
  - **Soporte para Recálculos Históricos**: Ante la rectificación de un resultado por parte del comité de arbitraje, la tabla de posiciones se recalcula de forma determinista basándose en la última fotografía auditada.

- **Contras**:
  - **Crecimiento en Memoria**: La lista de snapshots y eventos acumula objetos a lo largo de la vida del aggregate. En entornos de alta concurrencia requerirá estrategias de persistencia append-only en base de datos.
  - **Mayor Complejidad de Consulta**: Acceder al puntaje actual implica consultar el snapshot vigente (`latestSnapshot`) en vez de un atributo primitivo directo en la raíz de la entidad.

---

### 2.4 Clasificación Dinámica y Cadenas de Desempate (Comparator Composition)

- **Problema**:  
  Los torneos de robótica cuentan con reglas de desempate complejas que varían según la categoría. Por ejemplo: si dos robots igualan en puntos totales, desempata el menor tiempo; si empatan en tiempo, desempata la menor cantidad de penalizaciones; luego la puntuación de jueces, y finalmente un criterio determinista. Acoplar estas prioridades en un bloque monolítico de sentencias `if-else` hace que el algoritmo sea frágil y difícil de testear en aislamiento.

- **Solución Implementada**:  
  - Se implementó `TieBreakerChain`, basada en el patrón de **Composición de Comparadores (`Comparator<TeamScore>`)**.
  - La cadena encadena comparadores atómicos especializados:
    1. Mayor puntaje total (`HIGHEST_TOTAL_SCORE`).
    2. Menor tiempo de recorrido (`LOWEST_TIME_TAKEN`).
    3. Menor cantidad de penalizaciones (`LOWEST_PENALTIES`).
    4. Mayor puntuación subjetiva de jueces (`HIGHEST_JUDGE_SCORE`).
    5. Criterio determinista por identificador de equipo.
  - `RankingCalculatorService` utiliza esta cadena para ordenar los puntajes, asigna posiciones correlativas, y mediante `TieStatus` registra explícitamente si un equipo comparte posición con el anterior o el motivo por el cual desempató.

- **Pros**:
  - **Flexibilidad Configurativa**: Es posible alterar el orden de los criterios de desempate o incorporar criterios nuevos (ej. consumo energético o menor peso del robot) instanciando una cadena diferente sin alterar el algoritmo de ranking.
  - **Testeabilidad Aislada**: Cada regla de desempate se prueba unitariamente con casos de prueba específicos de igualdad parcial.
  - **Claridad Explicativa**: `RankingEntry` no solo reporta la posición sino el `TieStatus` que documenta formalmente si hubo empate o cómo se resolvió.

- **Contras**:
  - **Dependencia de la Información de Entrada**: Toda variable utilizada para desempatar debe estar consolidada previamente en `TeamScore` / `PerformanceSummary`.

- **Entrega 2**: la cadena pasa a declararse en el reglamento de cada desafío (`RankingScheme`, ver 2.10). `TieBreakerChain` sigue siendo la que usa `RankingCalculatorService` hasta que la tabla por desafío consuma `rulebook.rankingScheme()`.

---

### 2.5 Máquina de Estados Polimórfica para Apelaciones (State Pattern)

- **Problema**:  
  Las apelaciones de los equipos atraviesan un flujo legal estricto: presentación (`Pending`) -> revisión técnica por comité (`UnderReview`) -> resolución final (`Accepted` o `Rejected`). Resolver estas transiciones y sus restricciones mediante un campo `enum` o `String` y condicionales dispersos (`if (appeal.getStatus().equals("PENDING"))`) provocaría:
  1. Riesgo de transiciones ilegales (ej. aceptar un reclamo directamente sin pasar por revisión técnica, o modificar una apelación ya rechazada).
  2. Duplicación de lógica condicional en múltiples casos de uso.
  3. Acoplamiento a identificadores textuales o flags de estado.

- **Solución Implementada**:  
  - Se implementó el **Patrón State** puro con jerarquía polimórfica:
    - Interfaz `AppealState`.
    - Estados concretos: `PendingAppealState`, `UnderReviewAppealState`, `AcceptedAppealState`, `RejectedAppealState`.
  - Cada estado encapsula sus transiciones válidas y lanza excepciones de dominio (`IllegalStateException`) con mensajes expresivos ante operaciones no permitidas en su fase actual.
  - Se dotó a `AppealState` de **consultas polimórficas de negocio**:
    - `isPending()`, `isUnderReview()`, `isAccepted()`, `isRejected()`, `isResolved()`.
    - `canPublishOfficialRanking()`: Determina si el estado de la apelación bloquea la publicación del ranking oficial. Los estados finales (`Accepted`, `Rejected`) habilitan la publicación; los estados abiertos (`Pending`, `UnderReview`) la impiden.
  - En `PublishOfficialRankingUseCase`, la verificación de bloqueos se realiza polimórficamente sin ningún `if` de comparación por tipo:
    ```java
    boolean hasUnresolved = appealRepository.findByEditionAndCategory(editionId, categoryId).stream()
            .anyMatch(appeal -> !appeal.canPublishOfficialRanking());
    ```

- **Pros**:
  - **Eliminación Total de Condicionales de Tipo o Estado**: Desaparecen los `switch` y los `equals` de strings para preguntar el estado.
  - **Inviolabilidad de Transiciones**: Es arquitectónicamente imposible aceptar un reclamo pendiente sin haberlo puesto en revisión previa, o modificar una apelación en estado terminal.
  - **Extensibilidad Segura**: Agregar un estado intermedio (ej. `EscalatedToFederationState`) requiere implementar la interfaz sin tocar el código de los demás estados.

- **Contras**:
  - **Mayor Cantidad de Clases**: 4 clases concretas más la interfaz en lugar de una simple enumeración.
  - **Persistencia Futura**: Al conectar una base de datos relacional o de documentos, se requerirá un conversor (mapper) entre el nombre del estado persistido y la instancia polimórfica correspondiente.

---

### 2.6 Verificación Compuesta de Elegibilidad (Composite Specification Pattern)

- **Problema**:  
  Validar si un equipo puede inscribirse en una categoría involucra múltiples dimensiones ortogonales: edades de los integrantes, cantidad mínima y máxima de miembros, peso y dimensiones del robot, y documentación aprobada. Poner estas comprobaciones en `RegisterTeamUseCase` o dentro de `Team` sobrecargaría de responsabilidades al agregado o al servicio de aplicación.

- **Solución Implementada**:  
  - Se implementó el patrón **Specification** componible mediante la interfaz genérica `EligibilitySpecification<T>`.
  - Operaciones booleanas combinatorias de primer orden: `and()`, `or()`, `not()`.
  - Especificaciones atómicas de dominio:
    - `AgeLimitSpecification`: Comprueba el rango etario de los integrantes calculando la edad exacta a la fecha del torneo.
    - `TeamSizeSpecification`: Comprueba que el total de miembros cumpla los límites de la categoría.
    - `RobotSpecificationLimit`: Comprueba que masa y dimensiones del robot no violen los topes de la categoría delegando en `RobotLimits`.
    - `DocumentationVerifiedSpecification`: Comprueba que la documentación técnica requerida haya sido revisada y verificada.
  - `EligibilityResult` reporta el veredicto booleano junto con la lista detallada de causas de rechazo en lenguaje de dominio.

- **Pros**:
  - **Composabilidad Declarativa**: Permite armar conjuntos de reglas distintos para torneos colegiales, universitarios o internacionales simplemente encadenando `.and(...)`.
  - **Feedback Detallado y Explicativo**: Si un equipo es rechazado, se le devuelve la lista exacta de motivos (ej. *"Robot weight (2600g) exceeds category limit (2500g)"*, *"Team member age (27) exceeds maximum age (25)"*).
  - **Testeabilidad Unitaria Independiente**: Cada especificación se prueba de manera aislada con mocks o instancias mínimas.

- **Contras**:
  - **Costo de Evaluación**: Se evalúan múltiples reglas sobre colecciones en memoria, lo cual para listas muy masivas de equipos podría requerir optimizaciones de cortocircuito (aunque en competencias presenciales el volumen de equipos es perfectamente manejable).

---

### 2.7 Inversión de Control, Aislamiento del Dominio y Composition Root (`Main`)

- **Problema**:  
  Si los casos de uso o servicios de dominio crean internamente instancias concretas (`new InMemoryRepository()`, `new ConcreteService()`), quedan fuertemente acoplados a detalles de infraestructura. Del mismo modo, si el dominio importa herramientas de consola (`System.out`), frameworks de serialización (`Gson`) o clientes HTTP (`Unirest`), el negocio pierde pureza y portabilidad.

- **Solución Implementada**:  
  - **Dependencia Exclusiva de Interfaces**: El dominio solo conoce contratos (`TeamRepository`, `EditionRepository`, `AttemptRepository`, `RankingRepository`, `AppealRepository`).
  - **Inyección por Constructor**: Todas las dependencias requeridas por casos de uso y servicios se reciben explícitamente en el constructor. No existe ningún `new` de implementaciones concretas dentro de las clases de negocio.
  - **Composition Root Único ([`Main.java`](file:///c:/ITBA/2026/2026_Q2/Dps/tp/roboleague-domain/src/main/java/com/roboleague/Main.java))**: Es el único punto de ensamble donde se construyen las instancias de infraestructura en memoria, se configuran las cadenas de desempate y especificaciones de elegibilidad, se inyectan en los casos de uso y se orquesta la ejecución.
  - **Higiene de Importaciones**: Cero uso e importación de `System.out`, `Scanner`, `Gson` o `Unirest` en todo el paquete de dominio.

- **Pros**:
  - **Dominio 100% Puro y Portable**: El código de negocio puede ser reutilizado sin modificaciones en una aplicación Spring Boot, una API Quarkus, una CLI, o una arquitectura serverless.
  - **Testeabilidad Absoluta**: Los tests unitarios e integrados reemplazan cualquier dependencia por implementaciones en memoria o mocks sin alterar una sola línea de código de dominio.
  - **Facilidad de Mantenimiento**: El grafo completo de dependencias de la aplicación se comprende de un solo vistazo inspeccionando el Composition Root.

- **Contras**:
  - **Wiring Manual**: Al no utilizar un contenedor de inyección automático (ej. Spring Framework o Google Guice) en esta etapa, el ensamblado de clases en `Main` y en los tests de integración debe realizarse de forma explícita.

---

### 2.8 Desafíos con reglamento versionado (`Challenge` + `Rulebook`)

- **Problema**:  
  En la Entrega 1 leímos “desafío” como la forma de puntuar de una edición: una `Edition` era una sola prueba con un `ScoringPolicy`. La Entrega 2 rompe esa lectura: la demo pide tres desafíos en el mismo evento (uno mixto), y el reglamento define por desafío las mejores N de M rondas (F1) y el tope de bonificaciones (F2), y el desafío mixto (F3) declara qué fuentes exige. Con un reglamento por edición no hay dónde poner eso.

- **Solución Implementada**:
  - **`Challenge`** es un agregado propio que referencia su edición por id (`Edition` no crece y no conoce a sus desafíos). Rondas y tablas de posiciones van a ser por desafío y lo van a referenciar directo; hoy todavía se agrupan por edición y categoría.
  - **`Rulebook`** reemplaza a `ScoringPolicy`: una versión (`RulebookVersion`), cómo se puntúa (`ScoringScheme`, con al menos una regla; ver 2.12) y cómo se clasifica (`RankingScheme`, ver 2.10), sin exponer el composite. Es una clase y no un record para no publicar la regla compuesta como componente.
  - **Un desafío nunca está sin reglamento**: se crea con `Challenge.draft(id, edición, nombre).publish(reglas, esquema)`, que publica la v1. El borrador (`Draft`) no puede puntuar; solo sabe publicar. Así se evita un `Optional` en `currentRulebook()` y la falla de "desafío sin reglamento". El borrador es solo el paso de creación: el desafío copia sus datos y no lo guarda.
  - **El desafío numera las versiones**: `publish(reglas, esquema)` crea la siguiente (`v1`, `v2`…) y nunca edita la anterior. `currentRulebook()` da la vigente para capturar; `rulebook(versión)` da una exacta para recalcular con la versión con que se puntuó (lo que necesita el hallazgo 2).
  - **Category** sigue siendo elegibilidad (edad, composición, robot), no un desafío.
  - **Ids**: `ChallengeId` nace tipado porque es nuevo; el id de edición sigue siendo `String` hasta que se tipen los ids existentes (frente de inscripción).
  - **`Rulebook` es una entidad hija de `Challenge`**, identificada por su versión dentro del desafío; no es un value object comparable por valor.

- **Pros**:
  - Agregar un desafío es crear un `Challenge`, sin tocar `Edition` ni las reglas existentes.
  - Dentro de un desafío no puede haber dos “v1” distintas: la versión la asigna `Challenge` al publicar, no quien carga el reglamento.
  - F1, F2 y F3 tienen dónde vivir: el reglamento de cada desafío.

- **Contras**:
  - Los casos de uso de captura y apelación reciben el id del desafío. `ResolveAppealUseCase.acceptAppeal` sigue recibiendo ids sueltos del llamador; que el intento recuerde con qué versión se evaluó queda para el frente de intentos.
  - Crear un desafío exige sus reglas iniciales: no se puede dar de alta "vacío" y cargar el reglamento después.

---

### 2.9 Recálculo: reprocesar posiciones, no re-evaluar métricas

- **Problema**:  
  La consigna pide dos cosas que se parecen y no son iguales: *“los resultados deben poder recalcularse utilizando exactamente la versión de reglas correspondiente”* y, en la tabla, *“Recálculo: reprocesar posiciones después de una corrección”*. Si se las fusiona, el caso de uso de ranking parecería tener que volver a correr todas las `ScoreRule` sobre cada `RawMetrics` cada vez que cambia una apelación.

- **Qué se eligió**:  
  - **Evaluar con el reglamento vigente del desafío** en el momento en que el puntaje nace: `CaptureAttemptResultUseCase` y `ResolveAppealUseCase.acceptAppeal` usan `challenge.currentRulebook()`. El snapshot guarda métricas y desglose; un reglamento publicado no cambia.  
  - **Recalcular el ranking** (`RecalculateRankingUseCase`) lee el último snapshot de cada intento, arma `TeamScore` y vuelve a ordenar con `TieBreakerChain`. Cumple la fila de la tabla: reprocesa **posiciones** después de una corrección.  
  - Existe `Attempt.recalculateWith(Rulebook)` como gancho para re-aplicar un reglamento sobre las mismas métricas. **No está cableado** al caso de uso de ranking: con reglamentos publicados inmutables, re-evaluar en cada recálculo duplicaría trabajo sin cambiar el resultado.

- **Pros**:  
  - El recálculo es barato y determinista: la tabla sigue a la fotografía vigente, no reinterpreta la pista.  
  - La versión del reglamento queda fijada cuando nace el snapshot. La edición 2027 no reescribe la 2026.  
  - Encaja la corrección por apelación: primero se evalúa de nuevo (métricas revisadas + reglamento vigente del desafío), después se reconstruye el ranking.

- **Contras**:  
  - Si un desafío publica una versión nueva, los snapshots viejos no se re-evalúan solos. Las versiones anteriores siguen disponibles en `Challenge.rulebook(versión)` para hacerlo cuando el intento guarde su versión.

---

### 2.10 Esquema de clasificación en el reglamento (F1 + hallazgo 8)

- **Problema**:  
  F1 pide que el reglamento defina, por desafío, cuántas rondas cuentan (mejores N de M) y que la explicación diga cuáles se consideraron y cuáles se descartaron, sin tocar las reglas de puntaje. El hallazgo 8 marcó que la cadena de desempate se armaba en `Main`, fuera de la edición, y que la explicación del empate era siempre el mismo texto.

- **Solución Implementada** (paquete `evaluation.scheme`):
  - **`RankingScheme`** es la tercera parte del `Rulebook`, junto a la versión y las reglas: `rulebook.rankingScheme()`. Agrupa la selección de rondas y la cadena de desempate porque las dos responden a "cómo se clasifica en este desafío", y tiene comportamiento propio (`decide`, `compare`).
  - **Strategy `RoundSelection`**: `BestNOfM(n, m)` y `AllRounds`. Trabaja sobre puntajes ya calculados (`RoundScore`: ronda + snapshot vigente del intento), así que no toca ninguna `ScoreRule`, que es el criterio de aceptación de F1.
  - **`ChallengeScore`** es la explicación de F1: rondas consideradas, descartadas y la regla aplicada ("mejores 2 de 3 rondas"), con total, mejor tiempo, faltas y nota de jueces de las consideradas.
  - **`TieBreakCriterion`** con nombre de dominio (`HigherTotal`, `LowerTime`, `FewerPenalties`, `HigherJudgeScore`). `RankingScheme.decide` recorre la cadena en el orden declarado y devuelve un resultado `sealed`: `DecidedBy(criterio, orden)` o `Tied`. Que el desenlace sea un tipo propio y no un `if` sobre el tipo de criterio es lo que permite la regla #8.
  - **El total ordena primero**: la cadena siempre empieza con `HigherTotal` y no repite criterios; si no, `RankingScheme` la rechaza. Sin esa guarda, un reglamento con solo `lower-time` ordenaba ignorando el puntaje, y la consigna pide ordenar por puntaje y desempatar después.
  - **Ausencia sin relleno**: un equipo sin rondas no tiene tiempo, y una ronda sin panel de jueces no tiene nota (`OptionalDouble` vacío); quedan últimos en esos criterios en vez de usar `Double.MAX_VALUE` o 0 (regla #24).
  - **Por qué en `evaluation` y no en `ranking`**: el esquema es parte del reglamento, que vive en `evaluation`; `ranking` ya depende de `evaluation`, y al revés habría un ciclo de paquetes.

- **F1: clases agregadas**: `RankingScheme`, `RoundSelection`, `BestNOfM`, `AllRounds`, `RoundScore`, `ChallengeScore`, `TieBreakCriterion` y sus cuatro implementaciones, `TieBreakDecision`.
- **F1: clases modificadas**: `Rulebook` (tercer componente `rankingScheme`), `Challenge` (`Draft.publish` y `publish` reciben reglas y esquema), `DemoFixture` (Sumo declara mejores 2 de 3 y cuatro criterios encadenados); la demo actual es la de 2.15.
- **F1: refactors**: ninguno sobre las reglas de puntaje; es una pieza nueva que se enchufa en el reglamento.
- **F1: deuda que decidimos no resolver en este paso**:
  - **La tabla de posiciones todavía no usa el esquema**: `RankingCalculatorService` sigue con `TieBreakerChain`, toma el mejor intento por equipo y agrupa por edición, categoría y ronda, así que en la demo todavía no se ven rondas descartadas. Conectarlo es parte de la tabla por desafío (`Standings`): armar por equipo un `RoundScore` por ronda, ordenar con `rulebook.rankingScheme()` y explicar cada posición con `decide`. En ese mismo cambio se borran `TieBreakerChain` y `TeamScore.isTiedWith`, que hoy duplican los criterios (regla #27).
  - M no se valida contra las rondas programadas: `BestNOfM` rechaza más de M rondas al seleccionar, pero programar más de M es responsabilidad de la ronda por desafío.
  - Elegir el intento que representa a un equipo en una ronda (por ejemplo, el mejor no descalificado) queda en quien arma los `RoundScore`.
- **Patrones no aplicados**: Decorator sobre `RoundSelection` (no hay variación que lo pida). Los criterios por nombre llegaron después, con la configuración por API (`RuleCatalog`, 2.13).

- **Pros**: agregar una forma de seleccionar rondas o un criterio de desempate es una clase nueva, sin tocar las existentes (OCP); el orden del desempate es dato del reglamento y viaja con su versión; cada posición podrá explicar qué criterio la decidió cuando la tabla use el esquema.
- **Contras**: un criterio de desempate nuevo que mire otra métrica exige exponerla en `ChallengeScore`.

---

### 2.11 Reglas medidas y fuentes de resultado (demo + F3, lado del reglamento)

- **Problema**:  
  La demo pide diez tipos de reglas, una regla compuesta y un desafío mixto. F3 pide que un mismo turno reciba resultados de dos fuentes (mediciones automáticas y panel de jueces), que el puntaje quede pendiente hasta tener las dos y que la explicación detalle cada contribución por separado. El reglamento es quien sabe qué fuentes necesita un desafío y qué regla lee de cuál.

- **Solución Implementada**:
  - **`ResultSource`** (mediciones automáticas, panel de jueces) y **`ScoreRule.source()`**: cada regla declara de qué fuente lee. Las cinco reglas existentes devuelven una fuente fija; las nuevas la toman de la métrica que leen.
  - **`Metric(nombre, fuente)`**: una medición con nombre (precisión, víctimas rescatadas, salidas de línea…) que se lee de `RawMetrics.measurement(métrica)`. Si no fue capturada, falla nombrando la métrica en vez de devolver 0 (regla #24); es excepción y no resultado porque evaluar sin todas las fuentes no debería pasar: esperar las fuentes es el estado "esperando fuentes" del intento.
  - **Cuatro reglas nuevas**: `PrecisionRule`, `VictimsRule` (suma por rescatada y resta por abandonada), `MilestoneBonusRule` y `CountedFaultRule` (faltas contadas con franquicia). "Salidas de línea" y "Colisiones" son dos instancias de `CountedFaultRule` con otra métrica, no dos clases (reglas #6 y #7). Con las cinco existentes son nueve; el décimo tipo es el tope de bonificaciones (F2).
  - **Regla compuesta "Desempeño en pista"** = Tiempo + Objetivos, con `CompositeScoreRule`. Una regla compuesta exige que sus hijas lean de la misma fuente, para que su fuente esté definida.
  - **`Rulebook.requiredSources()`** y **`Rulebook.contributions(métricas)`**: las fuentes que exige el reglamento y el desglose separado por fuente (`SourceContribution`). El piso en cero y el tope de F2 quedan en el desglose total, no en una fuente. Como un reglamento mixto mezcla fuentes, las reglas se guardan en listas (hoy dentro del `ScoringScheme`, 2.12) en vez de envolverse en un `CompositeScoreRule`; combinar evaluaciones vive en un solo lugar (`RuleEvaluation.combining` y `RuleEvaluation.concat`).

- **F3 (lado del reglamento): clases agregadas**: `ResultSource`, `Metric`, `SourceContribution`, `CountedFaultRule`, `FaultTariff`, `PrecisionRule`, `VictimsRule`, `VictimTariff`, `MilestoneBonusRule`, `Milestone`.
- **Clases modificadas**: `ScoreRule` (`source()` y `RuleEvaluation.combining`), las cinco reglas existentes (`source()`), `CompositeScoreRule` (fuente única), `Rulebook` (lista de reglas, `requiredSources`, `contributions`), `RawMetrics` (`measurement`), `EvaluationFeedback` (`withMeasurements`), `DemoFixture` (Sumo puntúa con "Desempeño en pista"); la demo actual es la de 2.15.
- **Refactors**: `Rulebook` deja de usar un composite interno; `CompositeScoreRule.evaluateBreakdown` desaparece.
- **Deuda que decidimos no resolver en este paso**:
  - Las mediciones se leen por nombre (`String` dentro de `Metric`): un nombre mal escrito compila y recién falla al evaluar. Se cierra cuando el reglamento declare sus métricas (`MetricDefinition`) y la captura se valide contra ellas.
  - El intento todavía no espera sus fuentes ni guarda contribuciones: el estado "esperando fuentes", recibir cada fuente y armar las `RawMetrics` del turno son parte del intento y la captura. Un ajuste de faltas pierde las mediciones del intento (issue #5).
  - `averageJudgeScore()` sigue devolviendo 0 sin jueces en `RawMetrics`; con fuentes, un desafío que exige panel no debería evaluarse sin él.
  - Una medición fuera de rango (precisión fuera de 0 a 1, conteo no entero o negativo, más víctimas rescatadas que las del desafío) se rechaza con `IllegalArgumentException` al evaluar. Es un dato que entra por la captura, así que es una falla esperable: cuando la captura se valide contra las métricas del reglamento, ese rechazo pasa a ser un resultado de la captura (regla #28).
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
  - Nada impide poner una regla que resta (por ejemplo una penalización) en la lista de bonificaciones: lo obtenido puede ser negativo y el tope no recorta (lo documenta `BonusCapTest`). Clasificar cada regla como base, bonificación o penalización por tipo queda pendiente: quien configura el reglamento decide en qué lista va cada regla.
  - La demo muestra el tope desde 2.15: los intentos de Laberinto recortan 10 puntos de bonificaciones.
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
  - Los nombres de métrica siguen siendo texto libre hasta que el reglamento declare sus métricas (`MetricDefinition`, 2.11), y una regla que resta puede declararse como bonificación (2.12). Las dos cosas las pide la consigna en "Configuración de desafíos" y quedan para después de esta entrega.
  - `RuleCatalog.standard()` conoce las diez implementaciones desde el dominio. Son conceptos del dominio, no detalles técnicos, pero la lista podría armarse en el composition root (`UseCaseConfig`) para dejar la decisión más cerca del borde (regla #9).
  - Ciclo de paquetes `evaluation` ↔ `evaluation.definition` (por `Metric`), que se suma al de `evaluation` ↔ `evaluation.rules`. Se corta moviendo `Metric` y `ResultSource` a un paquete hoja.
  - La traducción JSON ↔ definición está escrita dos veces, en `RulebookBody` (API) y en `ChallengeMapper` (persistencia), porque cada borde fija su propia forma de datos; si las formas coinciden siempre, se puede compartir.
  - El catálogo convierte cualquier `IllegalArgumentException` de un constructor en un problema 422: también lo haría un bug. Validar antes de construir las claves que cada tipo exige dejaría el `catch` solo para invariantes de rango.

### 2.14 Persistencia del desafío

- **Solución Implementada**: tabla `challenges` (migración `V2`) con id, edición, nombre y una columna JSONB con todas las versiones del reglamento como definiciones. `ChallengeMapper` (capa anticorrupción, como `AppealMapper`) guarda `rulebook.definition()` y, al leer, reconstruye cada versión con `RuleCatalog` y el agregado con `Challenge.restore`, que exige versiones consecutivas desde 1. Si una versión guardada no se puede reconstruir es un dato corrupto: `IllegalStateException`.
- **Por qué una columna JSON y no tablas por regla**: un reglamento es un árbol (reglas compuestas, bonificaciones, estrategias) que se lee y se escribe entero y nunca se consulta por partes. Las versiones viejas no cambian. La forma del JSON la fijan records propios de infraestructura, no las clases del dominio.
- **Sin clave foránea a `editions`**: son agregados distintos y se referencian por id; la edición todavía no está en Postgres.
- **Deuda**: `Edition` sigue en memoria porque persistirla arrastra a los equipos inscriptos, que se van a modelar como inscripción (`Registration`). El perfil `demo` recarga todo en cada arranque, así que la demo no lo nota, pero sin ese perfil un desafío guardado puede quedar apuntando a una edición que ya no está en memoria.
- **Deuda: concurrencia.** Alta y publicación leen y después guardan sin bloqueo optimista: dos altas simultáneas con el mismo id, o dos publicaciones simultáneas, pueden pisarse en vez de dar 409. Se resuelve con `@Version` en la entidad cuando haga falta.
- **Deuda: dato corrupto.** Un reglamento guardado que no se puede reconstruir lanza `IllegalStateException`, que la convención de la API traduce a 409; un error propio de dato corrupto (500) queda pendiente.

### 2.15 Casos de uso y API de configuración; demo con tres desafíos

- **Casos de uso**: `CreateEditionUseCase`, `AddChallengeUseCase`, `PublishRulebookUseCase`, `GetChallengeUseCase`. Id repetido → `IllegalStateException` (409); edición o desafío inexistente → `IllegalArgumentException` (400, convención del equipo aunque REST usaría 404); reglamento rechazado → 422 con los problemas. La API valida los datos obligatorios de la edición antes de construir el dominio (un nulo llegaba como 500); `Edition` rechaza dos categorías con el mismo id, y `EditionHeader`, `Category` y `Challenge.Draft` rechazan ids y nombres en blanco.
- **API**: `POST /editions`, `POST /editions/{id}/challenges`, `POST /challenges/{id}/rulebook/versions`, `GET /challenges/{id}`. El JSON de un reglamento es el mismo al mandarlo y al leerlo (`RulebookBody`). Los DTOs son vistas planas del JSON, como `AppealDto`; la regla de tres parámetros se aplica al modelo.
- **Demo**: `DemoFixture` crea la edición y los tres desafíos con los casos de uso (`DemoRulebooks`), publica la v2 del Seguidor y corre el flujo de apelación sobre Laberinto. Rondas por desafío, captura del desafío mixto y tabla con N de M quedan para los frentes que los tienen.
- **Clases agregadas**: definiciones, `RuleCatalog`, `RulebookAssembly`, `Publication`, los cuatro casos de uso con sus comandos, `JpaChallengeRepository`, `ChallengeJpaEntity`, `ChallengeMapper`, `ChallengeController`, `EditionController`, `DemoRulebooks`.
- **Clases modificadas**: todas las reglas, estrategias y criterios (`definition()`/`from`/`code()`), `ScoringScheme`, `RankingScheme`, `Rulebook` (`definition()`), `Challenge` (`restore`), `UseCaseConfig`, `InMemoryRepositoryConfig` (sin bean en memoria de desafíos), `DemoConfig`, `DemoFixture`.

---

## 3. Matriz Comparativa Exhaustiva de Trade-offs

| Decisión Arquitectónica | Pros Clave | Contras y Costos Asociados | Alternativa Considerada y Rechazada |
| :--- | :--- | :--- | :--- |
| **Reificación en Value Objects (`Weight`, `Dimensions`, etc.)** | Erradica la obsesión por primitivos; garantiza invariantes; concentra el comportamiento con sus datos; simplifica firmas. | Mayor cantidad de clases/records; leve sobrecarga de instanciación en memoria. | Usar tipos primitivos sueltos (`double`, `int`). Rechazada por fragilidad e incoherencia de validaciones. |
| **Puntuación Explicable (`ScoreBreakdown` + `ScoreItem`)** | Transparencia total ante apelaciones; cada punto está respaldado por concepto y fórmula auditables. | Mayor sobrecarga de objetos por intento en comparación con un escalar numérico. | Retornar un `double` escalar directo. Rechazada porque impide la justificación reglamentaria y la auditoría. |
| **Reglas de Scoring con `Strategy` + `Composite`** | OCP estricto; permite combinar N reglas homogéneamente; desacopla el desafío del cálculo. | Requiere que `RawMetrics` aloje los campos necesarios para todas las reglas activas. | Codificar el cálculo con `switch-case` en el servicio. Rechazada por violación directa de OCP y SRP. |
| **Reglamento inmutable y versionado por desafío (`Rulebook`)** | Reproducibilidad histórica; una versión nueva no altera resultados puntuados con la anterior. | Hay que publicar una versión nueva para cualquier cambio de reglas. | Política global compartida mutable. Rechazada porque reescribiría o invalidaría resultados históricos. |
| **Append-Only Log con Snapshots y Eventos** | Trazabilidad forense no destructiva; preserva valores originales y cada ajuste con autor y motivo. | Crecimiento continuo de la colección de revisiones en memoria para procesos muy extensos. | Sobreescritura in-place (`attempt.setScore()`). Rechazada por violar la exigencia explícita de auditoría no destructiva. |
| **Cadenas de Desempate con `Comparator Composition`** | Flexibilidad total para reordenar o añadir criterios de desempate; testeo aislado de cada regla. | Exige que todas las métricas de desempate se encuentren consolidadas en `PerformanceSummary`. | Algoritmo rígido hardcodeado con `if-else` anidados. Rechazada por fragilidad y dificultad de extensión. |
| **Máquina de Estados Polimórfica (`AppealState`)** | Transiciones legalmente seguras; consultas polimórficas sin `if/switch`; cero strings de estado. | Proliferación de clases de estado; necesidad de mappers al momento de persistir en base de datos. | Campo `String` o `Enum` con bifurcaciones condicionales. Rechazada por riesgo de transiciones ilegales y código frágil. |
| **Elegibilidad con `Composite Specification`** | Composabilidad declarativa (`and`, `or`, `not`); desacoplamiento de criterios; detalle de causales de fallo. | Evaluación sucesiva en memoria; requiere recorrer las listas de integrantes y características del robot. | Validaciones manuales procedurales dentro del caso de uso. Rechazada por violar SRP y no ser reutilizable. |
| **Composition Root Único (`Main`)** | Dominio puro sin frameworks externos; inversión de dependencias estricta; máxima testeabilidad. | Requiere cableado manual explícito al no utilizar un framework de DI automático en el dominio. | Hacer `new` de implementaciones concretas adentro de servicios. Rechazada por acoplamiento indebido. |
| **`Challenge` como agregado con su `Rulebook`** | Varios desafíos por evento, cada uno con su reglamento versionado; F1, F2 y F3 viven en el reglamento del desafío. | Captura y apelación tienen que saber de qué desafío es el intento. | Una edición = una prueba (Entrega 1). Rechazada en la Entrega 2: la demo pide tres desafíos en un evento y reglas por desafío. |
| **Recálculo = reordenar snapshots vigentes** | Cumple “reprocesar posiciones después de una corrección”; no reinterpreta la pista; barato y determinista. | No re-aplica las `ScoreRule` si el desafío publica una versión nueva después de evaluar; que el intento recuerde su versión queda pendiente (hallazgo 2). | Re-evaluar todos los `RawMetrics` en cada recálculo de tabla. Rechazada: con reglamentos inmutables el resultado no cambia; el gancho `recalculateWith` queda por si el negocio lo pide. |
| **Tope de bonificaciones en el esquema de puntaje (`BonusLimit`)** | El tope va sobre la suma y queda explicado; ninguna regla de bonificación cambia. | Las bonificaciones se declaran aparte del resto de las reglas, y `Rulebook`/`Challenge.publish` pasaron a recibir un `ScoringScheme` en lugar de una lista de reglas. | Decorator que envuelve las bonificaciones como una regla más. Rechazada: el grupo mezcla fuentes y una regla declara una sola. |
| **Fuente declarada por cada regla (`ScoreRule.source()`)** | El reglamento sabe qué fuentes exige y separa el desglose por fuente sin `if` por tipo de regla. | Cada regla nueva tiene que declarar su fuente; una regla compuesta no puede mezclarlas. | Mapa fuente → reglas en el reglamento. Rechazada: duplica lo que la regla ya sabe. |
| **Esquema de clasificación en el reglamento (`RankingScheme`)** | Mejores N de M y desempate son datos del reglamento y viajan con su versión; cada criterio dice cuándo decidió. | La tabla todavía no lo consume (pendiente de la tabla por desafío). | Cadena de desempate armada en `Main`/config. Rechazada: dos desafíos no podrían desempatar distinto ni reproducir el criterio de una versión anterior. |

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
   - *Qué se eligió*: la versión del reglamento se aplica al **nacer** el puntaje (captura o apelación aceptada). `RecalculateRankingUseCase` reprocesa **posiciones** a partir del último snapshot. Es la lectura de la tabla de la consigna.
   - *Justificación del descarte*: con reglamentos publicados inmutables, re-evaluar no cambia el desglose y encarece el recálculo. El gancho en `Attempt` queda por si más adelante el negocio pide re-aplicar un reglamento sin cambiar métricas.
8. **Mejores N de M como una `ScoreRule` más**:
   - *Justificación del descarte*: una regla evalúa las métricas de un intento; N de M elige entre puntajes ya calculados de varias rondas. Meterlo en el motor de reglas obligaría a que una regla conozca otros intentos.
9. **Enum de criterios de desempate con comparadores (como `TieBreakerChain.StandardCriterion`)**:
   - *Justificación del descarte*: no dice qué criterio decidió y agregar uno obliga a abrir el enum (OCP). Cada criterio es una clase con nombre.

### Decisiones Técnicas Pospuestas (Justificación Arquitectónica):
1. **Framework de Persistencia Real (JPA / Hibernate / Spring Data)**:
   - *Decisión*: No incorporar dependencias de bases de datos relacionales ni ORMs en esta etapa.
   - *Justificación*: Conforme a los lineamientos de la consigna (*"Únicamente módulo del dominio; no se exige persistencia real ni API REST"*), diferir la persistencia mantiene el dominio desacoplado de esquemas relacionales, tablas o anotaciones de infraestructura.
2. **Contenedor de Inyección de Dependencias Automatizado (Spring / Guice / CDI)**:
   - *Decisión*: Ensamble explícito manual en `Main` y en fixtures de test.
   - *Justificación*: Evita la contaminación del modelo de dominio con anotaciones externas (`@Inject`, `@Autowired`, `@Component`), garantizando un artefacto de dominio puro en Java nativo estándar.
3. **Motor de Reglas Externo con DSL (Drools / Rete)**:
   - *Decisión*: Resolver el scoring mediante composición de objetos Java puros (`ScoreRule`).
   - *Justificación*: El patrón Strategy + Composite ofrece tipado fuerte en tiempo de compilación, velocidad máxima de ejecución, cero overhead de parsing en runtime y máxima facilidad de depuración mediante pruebas unitarias nativas de JUnit 5.

---

## 5. Estrategia de Verificación y Cobertura de Pruebas

El diseño implementado se valida con pruebas automatizadas en `src/test/java` de cada módulo, divididas en:
- **Pruebas Unitarias de Scoring y Auditoría**:
  - `ScoringEngineTest`: Evalúa el comportamiento de cada regla elemental (`TimeBasedRule`, `ObjectiveBonusRule`, `PenaltyRule`, `JudgeSubjectiveRule`) y su agregación en un `Rulebook`, verificando los desgloses paso a paso.
  - `RulebookReviewFindingsTest`: Los tests del informe de la Entrega 1 sobre el reglamento (hallazgo 1 y la pregunta del desglose), invertidos para describir el comportamiento correcto. Incluye un test parametrizado con cada regla que rechaza parámetros negativos.
  - `RuleCatalogTest`: Verifica, con casos parametrizados, que cada tipo de regla, estrategia y criterio se describe y se reconstruye igual y puntúa lo mismo, y que una definición inválida (tipo desconocido, parámetro o métrica faltante, parámetro inválido) se rechaza nombrando el problema.
  - `ConfigureChallengesUseCaseTest`: crear edición, agregar desafío, publicar versión, y sus rechazos.
  - `JpaChallengeRepositoryTest` (Postgres): un desafío vuelve con todas sus versiones, cada versión puntúa igual que antes (tope incluido) y el restaurado publica la siguiente.
  - `ChallengeControllerTest` (API): 201, 400, 409 y 422 de cada endpoint de configuración. `DemoFixtureTest` verifica los tres desafíos, la v2 del Seguidor, las fuentes de Rescate y el tope en el desglose.
  - `EditionTest` y `EditionControllerTest` (API): una edición no ofrece dos categorías con el mismo id, no acepta ids ni nombres en blanco, y un dato obligatorio faltante es 400 y no 500.
  - `BonusCapTest`: Verifica, con casos parametrizados, que el tope recorta solo lo que la suma de bonificaciones supera, que se aplica sobre el conjunto y no sobre cada una, que la explicación muestra lo obtenido, el tope y el recorte, y que agregar el tope no cambia lo que da cada regla de bonificación.
  - `MeasuredRulesTest` y `RulebookSourcesTest`: Verifican, con casos parametrizados, las reglas nuevas (faltas con franquicia, precisión, víctimas, hito), que una medición faltante no se convierte en cero, la fuente que declara cada regla, que una regla compuesta no mezcle fuentes, las fuentes que exige un reglamento y que, sin tope, las contribuciones por fuente sumen el total.
  - `BestNOfMTest` y `RankingSchemeTest`: Verifican, con casos parametrizados, qué rondas cuentan con mejores N de M y cuáles se descartan, que el primer criterio que separa a dos equipos dé su nombre, el empate en todos los criterios y que el orden declarado cambie al ganador.
  - `ChallengeTest`: Verifica que cada publicación del reglamento crea la versión siguiente, que una versión nueva no cambia cómo puntúa la anterior y que se puede pedir una versión exacta.
  - `AttemptAuditTrailTest`: Valida que cada modificación sobre un intento genere snapshots inmutables con numeración correlativa, preservando la revisión original intacta y registrando eventos de dominio. Cubre además la descalificación auditada (`AttemptDisqualifiedEvent`) y la restauración del estado del intento tras una apelación rechazada.
- **Pruebas Unitarias de Dominio y Flujos de Estado**:
  - `AppealStateFlowTest`: Verifica la imposibilidad de transiciones ilegales en la máquina de estados de apelaciones y comprueba las consultas polimórficas de habilitación de publicación oficial.
  - `TieBreakerRankingTest`: Valida el comportamiento de `TieBreakerChain` resolviendo empates por mayor puntaje, menor tiempo, menores faltas y notas de jueces, documentando la justificación en `TieStatus`.
  - `EligibilitySpecificationTest`: Comprueba el funcionamiento de las especificaciones compuestas de edad, integrantes, dimensiones y peso del robot, y documentación aprobada.
- **Pruebas de Casos de Uso y de Integración de Punta a Punta**:
  - `RegisterTeamUseCaseTest`: Verifica la correcta admisión y el rechazo fundamentado de equipos según su elegibilidad.
  - `ScheduleRoundUseCaseTest`: Verifica la generación ordenada de slots, asignación balanceada de jueces y pistas, y prevención de turnos solapados.
  - `PublishOfficialRankingUseCaseTest`: Comprueba el bloqueo automático de la publicación del ranking oficial mientras existan apelaciones abiertas o en revisión sobre intentos de ese ranking, que apelaciones de otras rondas no bloquean, y que un ranking oficial no puede republicarse.
  - `ResolveAppealUseCaseTest`: Verifica que rechazar una apelación conserva el puntaje original y libera el intento del estado de apelación.
  - `AppealAndRecalculateIntegrationTest`: **Prueba de integración end-to-end** que ejecuta el ciclo de vida completo: Registro de equipos -> Planificación de ronda -> Captura inicial de intentos -> Cálculo de ranking provisional -> Presentación de apelación por controversia en penalizaciones -> Bloqueo de publicación oficial -> Revisión técnica arbitral -> Aceptación del reclamo con métricas corregidas -> Verificación del rastro de auditoría en el intento -> Recálculo automático del ranking con inversión legítima de posiciones -> Publicación exitosa del ranking oficial.
