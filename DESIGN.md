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
3. **`evaluation`**: Corazón computacional del sistema. Modela los intentos en pista (`Attempt`), la captura estructurada de métricas observadas (`RawMetrics`), el motor de puntuación explicable y versionado (`ScoringPolicy`, `ScoreRule`, `ScoreBreakdown`), y el rastro de auditoría append-only con snapshots inmutables y eventos de dominio.
4. **`ranking`**: Consolida los puntajes agregados por equipo (`TeamScore`), resuelve empates jerárquicamente mediante cadenas desacopladas (`TieBreakerChain`), administra la publicación oficial o provisional de tablas (`Ranking`), y gobierna el ciclo de apelaciones (`Appeal`) mediante una máquina de estados polimórfica.

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
  - **Patrón Composite**: `CompositeScoreRule` permite agrupar múltiples reglas elementales (`TimeBasedRule`, `ObjectiveBonusRule`, `PenaltyRule`, `JudgeSubjectiveRule`, `ResourceConsumptionRule`) y evaluarlas como una única unidad compuesta.
  - **Explicabilidad mediante Value Objects (`ScoreBreakdown` y `ScoreItem`)**: Al evaluar un intento en pista (`Attempt`), la regla no retorna un número escalar (`double`), sino un desglose inmutable que detalla:
    - Concepto evaluado (`concept`).
    - Métrica observada (`rawMetric`).
    - Fórmula matemática aplicada con sus coeficientes (`appliedFormula`).
    - Subtotal parcial calculado (`subtotal`).
    - Notas y justificaciones reglamentarias.
  - **Inmutabilidad del Reglamento (`ScoringPolicy`) por Edición**: Cada `Edition` se enlaza con una instancia inmutable y versionada del reglamento (ej. `"v1.0.2026"`). Al recalcular intentos pasados, se garantiza reproducibilidad histórica absoluta.

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

## 3. Matriz Comparativa Exhaustiva de Trade-offs

| Decisión Arquitectónica | Pros Clave | Contras y Costos Asociados | Alternativa Considerada y Rechazada |
| :--- | :--- | :--- | :--- |
| **Reificación en Value Objects (`Weight`, `Dimensions`, etc.)** | Erradica la obsesión por primitivos; garantiza invariantes; concentra el comportamiento con sus datos; simplifica firmas. | Mayor cantidad de clases/records; leve sobrecarga de instanciación en memoria. | Usar tipos primitivos sueltos (`double`, `int`). Rechazada por fragilidad e incoherencia de validaciones. |
| **Puntuación Explicable (`ScoreBreakdown` + `ScoreItem`)** | Transparencia total ante apelaciones; cada punto está respaldado por concepto y fórmula auditables. | Mayor sobrecarga de objetos por intento en comparación con un escalar numérico. | Retornar un `double` escalar directo. Rechazada porque impide la justificación reglamentaria y la auditoría. |
| **Reglas de Scoring con `Strategy` + `Composite`** | OCP estricto; permite combinar N reglas homogéneamente; desacopla el desafío del cálculo. | Requiere que `RawMetrics` aloje los campos necesarios para todas las reglas activas. | Codificar el cálculo con `switch-case` en el servicio. Rechazada por violación directa de OCP y SRP. |
| **Reglamento Inmutable por Edición (`ScoringPolicy`)** | Reproducibilidad histórica perfecta; cambios en ediciones futuras no alteran resultados pasados. | Requiere instanciar o clonar plantillas de reglas para cada nueva edición del torneo. | Política global compartida mutable. Rechazada porque reescribiría o invalidaría torneos históricos. |
| **Append-Only Log con Snapshots y Eventos** | Trazabilidad forense no destructiva; preserva valores originales y cada ajuste con autor y motivo. | Crecimiento continuo de la colección de revisiones en memoria para procesos muy extensos. | Sobreescritura in-place (`attempt.setScore()`). Rechazada por violar la exigencia explícita de auditoría no destructiva. |
| **Cadenas de Desempate con `Comparator Composition`** | Flexibilidad total para reordenar o añadir criterios de desempate; testeo aislado de cada regla. | Exige que todas las métricas de desempate se encuentren consolidadas en `PerformanceSummary`. | Algoritmo rígido hardcodeado con `if-else` anidados. Rechazada por fragilidad y dificultad de extensión. |
| **Máquina de Estados Polimórfica (`AppealState`)** | Transiciones legalmente seguras; consultas polimórficas sin `if/switch`; cero strings de estado. | Proliferación de clases de estado; necesidad de mappers al momento de persistir en base de datos. | Campo `String` o `Enum` con bifurcaciones condicionales. Rechazada por riesgo de transiciones ilegales y código frágil. |
| **Elegibilidad con `Composite Specification`** | Composabilidad declarativa (`and`, `or`, `not`); desacoplamiento de criterios; detalle de causales de fallo. | Evaluación sucesiva en memoria; requiere recorrer las listas de integrantes y características del robot. | Validaciones manuales procedurales dentro del caso de uso. Rechazada por violar SRP y no ser reutilizable. |
| **Composition Root Único (`Main`)** | Dominio puro sin frameworks externos; inversión de dependencias estricta; máxima testeabilidad. | Requiere cableado manual explícito al no utilizar un framework de DI automático en el dominio. | Hacer `new` de implementaciones concretas adentro de servicios. Rechazada por acoplamiento indebido. |

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
   - *Justificación del descarte*: Viola el principio de Responsabilidad Única (SRP) y Abierto/Cerrado (OCP). La entidad `Attempt` debe modelar la ejecución física y la auditoría de un intento, no el conocimiento de todas las fórmulas matemáticas de todas las categorías de robótica existentes.

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

El diseño implementado se valida mediante una suite completa de **27 pruebas automatizadas** en `src/test/java`, divididas en:
- **Pruebas Unitarias de Scoring y Auditoría**:
  - `ScoringEngineTest`: Evalúa el comportamiento de cada regla elemental (`TimeBasedRule`, `ObjectiveBonusRule`, `PenaltyRule`, `JudgeSubjectiveRule`) y su agregación en `CompositeScoreRule`, verificando los desgloses paso a paso.
  - `AttemptAuditTrailTest`: Valida que cada modificación sobre un intento genere snapshots inmutables con numeración correlativa, preservando la revisión original intacta y registrando eventos de dominio.
- **Pruebas Unitarias de Dominio y Flujos de Estado**:
  - `AppealStateFlowTest`: Verifica la imposibilidad de transiciones ilegales en la máquina de estados de apelaciones y comprueba las consultas polimórficas de habilitación de publicación oficial.
  - `TieBreakerRankingTest`: Valida el comportamiento de `TieBreakerChain` resolviendo empates por mayor puntaje, menor tiempo, menores faltas y notas de jueces, documentando la justificación en `TieStatus`.
  - `EligibilitySpecificationTest`: Comprueba el funcionamiento de las especificaciones compuestas de edad, integrantes, dimensiones y peso del robot, y documentación aprobada.
- **Pruebas de Casos de Uso y de Integración de Punta a Punta**:
  - `RegisterTeamUseCaseTest`: Verifica la correcta admisión y el rechazo fundamentado de equipos según su elegibilidad.
  - `ScheduleRoundUseCaseTest`: Verifica la generación ordenada de slots, asignación balanceada de jueces y pistas, y prevención de turnos solapados.
  - `PublishOfficialRankingUseCaseTest`: Comprueba el bloqueo automático de la publicación del ranking oficial mientras existan apelaciones abiertas o en revisión.
  - `AppealAndRecalculateIntegrationTest`: **Prueba de integración end-to-end** que ejecuta el ciclo de vida completo: Registro de equipos -> Planificación de ronda -> Captura inicial de intentos -> Cálculo de ranking provisional -> Presentación de apelación por controversia en penalizaciones -> Bloqueo de publicación oficial -> Revisión técnica arbitral -> Aceptación del reclamo con métricas corregidas -> Verificación del rastro de auditoría en el intento -> Recálculo automático del ranking con inversión legítima de posiciones -> Publicación exitosa del ranking oficial.
