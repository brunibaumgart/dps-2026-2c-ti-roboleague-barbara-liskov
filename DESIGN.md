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

| Módulo | Responsabilidad y dependencia de producción |
| --- | --- |
| `roboleague-domain` | Modelo, invariantes, servicios de dominio y puertos; Java sin frameworks |
| `roboleague-application` | Casos de uso que coordinan el dominio; depende de domain |
| `roboleague-infrastructure` | Adaptadores JPA/Postgres y en memoria; depende de domain |
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
4. **`ranking`**: Consolida los puntajes agregados por equipo (`TeamScore`), resuelve empates jerárquicamente mediante cadenas desacopladas (`TieBreakerChain`), administra la publicación oficial o provisional de tablas (`Ranking`), y gobierna el ciclo de apelaciones (`Appeal`) mediante una máquina de estados polimórfica.

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
  - Las reglas evalúan EligibilityCandidate (Team, Category y fecha explícita).
    RegistrationEligibility reúne la política estándar, reutilizada en inscripción,
    actualización, cambio de categoría y programación; la referencia es el inicio
    de la edición.
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

### 2.7 Inversión de Control, Aislamiento del Dominio y Composition Root

- **Problema**:  
  Si los casos de uso o servicios de dominio crean internamente instancias concretas (`new InMemoryRepository()`, `new ConcreteService()`), quedan fuertemente acoplados a detalles de infraestructura. Del mismo modo, si el dominio importa herramientas de consola (`System.out`), frameworks de serialización (`Gson`) o clientes HTTP (`Unirest`), el negocio pierde pureza y portabilidad.

- **Solución Implementada**:  
  - **Puertos internos**: Los casos de uso conocen contratos de persistencia definidos en domain: `TeamRepository`, `EditionRepository`, `RoundRepository`, `ChallengeRepository`, `AttemptRepository`, `RankingRepository` y `AppealRepository`. No dependen de los adaptadores JPA o en memoria.
  - **Inyección por Constructor**: Los casos de uso reciben puertos y servicios; construir objetos del modelo dentro del negocio es válido. Evitar la creación interna de adaptadores tecnológicos.
  - **Composition Root actual**: [`UseCaseConfig`](roboleague-api/src/main/java/com/roboleague/api/config/UseCaseConfig.java) registra servicios y casos de uso. [`InMemoryRepositoryConfig`](roboleague-api/src/main/java/com/roboleague/api/config/InMemoryRepositoryConfig.java) registra los puertos que siguen en memoria; Spring descubre los adaptadores JPA. [`RoboLeagueApplication`](roboleague-api/src/main/java/com/roboleague/RoboLeagueApplication.java) inicia la aplicación. El ensamblado manual en `Main` corresponde a la Entrega 1 y ya no describe la ejecución actual.
  - **Transacciones externas al núcleo**: [`TransactionalUseCases`](roboleague-api/src/main/java/com/roboleague/api/config/TransactionalUseCases.java) aplica proxies de clase a beans del paquete exacto `com.roboleague.usecase`. El rollback cubre escrituras Postgres, no cambios de repositorios en memoria.
  - **Higiene de Importaciones**: Cero uso e importación de `System.out`, `Scanner`, `Gson` o `Unirest` en todo el paquete de dominio.

- **Pros**:
  - **Dominio 100% Puro y Portable**: El código de negocio puede ser reutilizado sin modificaciones en una aplicación Spring Boot, una API Quarkus, una CLI, o una arquitectura serverless.
  - **Pruebas del núcleo sin frameworks**: Los tests de dominio y casos de uso pueden usar objetos Java, adaptadores en memoria o mocks. Las garantías de JPA, concurrencia, transacciones y HTTP requieren sus pruebas de integración.
  - **Facilidad de Mantenimiento**: El grafo completo de dependencias de la aplicación se comprende de un solo vistazo inspeccionando el Composition Root.

- **Contras**:
  - **Configuración de ensamblado**: Aunque Spring gestiona los beans, se mantienen registros explícitos de casos de uso y selección de adaptadores. Debe haber un único bean por puerto.
  - **Restricciones de proxies**: La interceptación transaccional depende del paquete y de clases/métodos aptos para proxies. Cambiar esa estructura exige verificar el cableado.

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
  - **Evaluar con la versión del reglamento del intento** (actualizado en 2.18): el intento se abre con la versión vigente de su desafío y la guarda; cada revisión, también la de una apelación aceptada, se puntúa con esa versión aunque el desafío publique otra. El snapshot guarda la versión, las métricas y el desglose.  
  - **Recalcular el ranking** (`RecalculateRankingUseCase`) lee el último snapshot de cada intento, arma `TeamScore` y vuelve a ordenar con `TieBreakerChain`. Cumple la fila de la tabla: reprocesa **posiciones** después de una corrección.  
  - No se re-aplica un reglamento sobre las mismas métricas: `Attempt.recalculateWith`, que lo permitía con cualquier versión, se borró en 2.18.

- **Pros**:  
  - El recálculo es barato y determinista: la tabla sigue a la fotografía vigente, no reinterpreta la pista.  
  - La versión del reglamento queda fijada cuando nace el snapshot. La edición 2027 no reescribe la 2026.  
  - Encaja la corrección por apelación: primero se evalúa de nuevo (métricas revisadas + reglamento vigente del desafío), después se reconstruye el ranking.

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
  - **Cuatro reglas nuevas**: `PrecisionRule`, `VictimsRule` (suma por rescatada y resta por abandonada; desde 2.17 la resta es `AbandonedVictimsRule`), `MilestoneBonusRule` y `CountedFaultRule` (faltas contadas con franquicia). "Salidas de línea" y "Colisiones" son dos instancias de `CountedFaultRule` con otra métrica, no dos clases (reglas #6 y #7). Con las cinco existentes son nueve; el décimo tipo es el tope de bonificaciones (F2).
  - **Regla compuesta "Desempeño en pista"** = Tiempo + Objetivos, con `CompositeScoreRule`. Una regla compuesta exige que sus hijas lean de la misma fuente, para que su fuente esté definida.
  - **`Rulebook.requiredSources()`** y **`Rulebook.contributions(métricas)`**: las fuentes que exige el reglamento y el desglose separado por fuente (`SourceContribution`). El piso en cero y el tope de F2 quedan en el desglose total, no en una fuente. Como un reglamento mixto mezcla fuentes, las reglas se guardan en listas (hoy dentro del `ScoringScheme`, 2.12) en vez de envolverse en un `CompositeScoreRule`; combinar evaluaciones vive en un solo lugar (`RuleEvaluation.combining` y `RuleEvaluation.concat`).

- **F3 (lado del reglamento): clases agregadas**: `ResultSource`, `Metric`, `SourceContribution`, `CountedFaultRule`, `FaultTariff`, `PrecisionRule`, `VictimsRule`, `VictimTariff`, `MilestoneBonusRule`, `Milestone`.
- **Clases modificadas**: `ScoreRule` (`source()` y `RuleEvaluation.combining`), las cinco reglas existentes (`source()`), `CompositeScoreRule` (fuente única), `Rulebook` (lista de reglas, `requiredSources`, `contributions`), `RawMetrics` (`measurement`), `EvaluationFeedback` (`withMeasurements`), `DemoFixture` (Sumo puntúa con "Desempeño en pista"); la demo actual es la de 2.15.
- **Refactors**: `Rulebook` deja de usar un composite interno; `CompositeScoreRule.evaluateBreakdown` desaparece.
- **Deuda que decidimos no resolver en este paso**:
  - ~~Las mediciones se leen por nombre: un nombre mal escrito compila y recién falla al evaluar.~~ Cerrada en 2.16: el reglamento declara sus métricas y rechaza una regla que lee una no declarada.
  - El intento todavía no espera sus fuentes ni guarda contribuciones: el estado "esperando fuentes", recibir cada fuente y armar las `RawMetrics` del turno son parte del intento y la captura. Un ajuste de faltas pierde las mediciones del intento (issue #5).
  - `averageJudgeScore()` sigue devolviendo 0 sin jueces en `RawMetrics`; con fuentes, un desafío que exige panel no debería evaluarse sin él.
  - Una medición fuera de rango (precisión fuera de 0 a 1, conteo no entero o negativo, más víctimas rescatadas que las del desafío) se rechaza con `IllegalArgumentException` al evaluar. Desde 2.16 la captura se puede revisar antes con `Rulebook.check`, que devuelve el rechazo como resultado (regla #28); la excepción al evaluar queda para una invariante rota. Falta que el caso de uso de captura (frente 2) lo llame.
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
  - ~~Una regla que resta puede declararse como bonificación (2.12) y los nombres de métrica son texto libre.~~ Cerradas en 2.17 y 2.16.
  - `RuleCatalog.standard()` conoce las diez implementaciones desde el dominio. Son conceptos del dominio, no detalles técnicos, pero la lista podría armarse en el composition root (`UseCaseConfig`) para dejar la decisión más cerca del borde (regla #9).
  - Ciclo de paquetes `evaluation` ↔ `evaluation.definition` (por `Metric`), que se suma al de `evaluation` ↔ `evaluation.rules`. Se corta moviendo `Metric` y `ResultSource` a un paquete hoja.
  - La traducción JSON ↔ definición está escrita dos veces, en `RulebookBody` (API) y en `ChallengeMapper` (persistencia), porque cada borde fija su propia forma de datos; si las formas coinciden siempre, se puede compartir.
  - El catálogo convierte cualquier `IllegalArgumentException` de un constructor en un problema 422: también lo haría un bug. Validar antes de construir las claves que cada tipo exige dejaría el `catch` solo para invariantes de rango.

### 2.14 Persistencia del desafío

- **Solución Implementada**: tabla `challenges` (migración `V2`) con id, edición, nombre y una columna JSONB con todas las versiones del reglamento como definiciones. `ChallengeMapper` (capa anticorrupción, como `AppealMapper`) guarda `rulebook.definition()` y, al leer, reconstruye cada versión con `RuleCatalog` y el agregado con `Challenge.restore`, que exige versiones consecutivas desde 1. Si una versión guardada no se puede reconstruir es un dato corrupto: `IllegalStateException`.
- **Por qué una columna JSON y no tablas por regla**: un reglamento es un árbol (reglas compuestas, bonificaciones, estrategias) que se lee y se escribe entero y nunca se consulta por partes. Las versiones viejas no cambian. La forma del JSON la fijan records propios de infraestructura, no las clases del dominio.
- **Sin clave foránea a `editions`**: son agregados distintos y se referencian por id; la edición todavía no está en Postgres.
- **Deuda**: `Edition` y Team siguen en memoria; la spec 03 ya separó su relación mediante Registration. Sus adaptadores JPA corresponden al frente 5. El perfil `demo` recarga todo en cada arranque, así que la demo no lo nota, pero sin ese perfil un desafío guardado puede quedar apuntando a una edición que ya no está en memoria.
- **Deuda: concurrencia.** Alta y publicación leen y después guardan sin bloqueo optimista: dos altas simultáneas con el mismo id, o dos publicaciones simultáneas, pueden pisarse en vez de dar 409. Se resuelve con `@Version` en la entidad cuando haga falta.
- **Deuda: dato corrupto.** Un reglamento guardado que no se puede reconstruir lanza `IllegalStateException`, que la convención de la API traduce a 409; un error propio de dato corrupto (500) queda pendiente.

### 2.15 Casos de uso y API de configuración; demo con tres desafíos

- **Casos de uso**: `CreateEditionUseCase`, `AddChallengeUseCase`, `PublishRulebookUseCase`, `GetChallengeUseCase`. Id repetido → `IllegalStateException` (409); edición o desafío inexistente → `IllegalArgumentException` (400, convención del equipo aunque REST usaría 404); reglamento rechazado → 422 con los problemas. La API valida los datos obligatorios de la edición antes de construir el dominio (un nulo llegaba como 500); `Edition` rechaza dos categorías con el mismo id, y `EditionHeader`, `Category` y `Challenge.Draft` rechazan ids y nombres en blanco.
- **API**: `POST /editions`, `POST /editions/{id}/challenges`, `POST /challenges/{id}/rulebook/versions`, `GET /challenges/{id}`. El JSON de un reglamento es el mismo al mandarlo y al leerlo (`RulebookBody`). Los DTOs son vistas planas del JSON, como `AppealDto`; la regla de tres parámetros se aplica al modelo.
- **Demo**: `DemoFixture` crea la edición y los tres desafíos con los casos de uso (`DemoRulebooks`), publica la v2 del Seguidor y corre el flujo de apelación sobre Laberinto. Rondas por desafío, captura del desafío mixto y tabla con N de M quedan para los frentes que los tienen.
- **Clases agregadas**: definiciones, `RuleCatalog`, `RulebookAssembly`, `Publication`, los cuatro casos de uso con sus comandos, `JpaChallengeRepository`, `ChallengeJpaEntity`, `ChallengeMapper`, `ChallengeController`, `EditionController`, `DemoRulebooks`.
- **Clases modificadas**: todas las reglas, estrategias y criterios (`definition()`/`from`/`code()`), `ScoringScheme`, `RankingScheme`, `Rulebook` (`definition()`), `Challenge` (`restore`), `UseCaseConfig`, `InMemoryRepositoryConfig` (sin bean en memoria de desafíos), `DemoConfig`, `DemoFixture`.

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
- **Clases modificadas**: `ScoreRule` y las diez reglas (`metrics()`), `ScoringScheme`, `Rulebook` (`check`, `definition`), `RulebookDefinition`, `RuleCatalog`, `RulebookBody`, `RulebookDto`, `ChallengeJpaEntity`, `ChallengeMapper`, `DemoRulebooks` y los tests que arman reglamentos con reglas medidas.
- **Refactors**: `ScoringScheme` agrupa sus listas en `ScoreRules`; el resto de las reglas no cambia cómo puntúa.
- **Alternativas descartadas**:
  - Que las reglas referencien la métrica solo por nombre y tomen fuente, unidad y rango de la declaración: más limpio, pero cambia la definición de las cuatro reglas medidas y el JSON de cada regla. Se dejó la referencia `{"name", "source"}` y se valida que coincida.
  - Validar la captura dentro del caso de uso de captura: el caso de uso es del frente 2 y se está rehaciendo para F3; el reglamento ofrece `check` y el caso de uso lo llama.
  - Un cuarto parámetro en `ScoringScheme` o en `Rulebook`: rompe la regla #2.
- **Patrones no aplicados**: Specification para las métricas (no hay combinaciones `and`/`or`: cada métrica se revisa sola contra su definición).
- **Deuda que decidimos no resolver en este paso**:
  - Tiempo, objetivos, faltas, consumo y notas de jueces siguen siendo campos fijos de `TrackPerformance`/`EvaluationFeedback` y no se declaran. Pasarlos a métricas declaradas toca el intento y la persistencia de apelaciones (frentes 2 y 4); con eso se podría unificar `PenaltyRule` con `CountedFaultRule` (2.11).
  - Los reglamentos guardados antes de este cambio no tienen `metrics`: si alguna regla lee una métrica con nombre ya no se reconstruyen (`IllegalStateException`). Hoy solo hay datos de la demo, que se recargan en cada arranque.
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
  - `TieBreakerChain` y `TeamScore` siguen contando faltas hasta que la tabla use `RankingScheme` (2.10).
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
  - **Puntaje computable**: `countableScore()` es el desglose que cuenta, vacío antes de puntuar o si está descalificado (regla #24). `TeamScore` solo usa intentos que cuentan; un equipo sin ninguno no suma.
  - **Validación contra la ronda**: `ScheduleRoundUseCase` guarda la ronda en un `RoundRepository` mínimo (`save`, `findBySlotId`); `Round.slot(id)` y `Slot.isJudgedBy(juez)`. `ReceiveResultUseCase` exige que el turno exista (400) y que el juez esté asignado (422), toma el equipo del slot y abre el intento con la versión vigente del desafío.
  - **API**: `PUT /attempts/{id}/measurements`, `PUT /attempts/{id}/judge-scores` y `GET /attempts/{id}/breakdown`, que muestra lo pendiente y lo que aportó cada fuente (README, "Cargar los resultados de un intento").
  - **Persistencia**: migración V3 con el turno, la versión y la etapa en columnas, y las entregas, revisiones y eventos en JSONB. `Attempt.restore(identidad, referencia, AttemptProgress)` reconstruye sin repetir transiciones, como `Appeal.restore`; `AttemptStage` es el estado como dato y el único lugar que elige un estado por su nombre (regla #9). `AttemptEvent` pasa a ser sellado para que el mapper guarde cada tipo.
  - **Issue #5**: `RawMetrics.withPenalties` cambia solo las faltas.

- **Impacto en la demo**: Laberinto no cambia (TitanTeam 197,5 → 257,5 con la apelación, CyberTeam 235). En Rescate, CyberTeam recibe las mediciones y el panel y suma 147,5; TitanTeam tiene las mediciones y espera el panel.
- **Clases agregadas**: `AttemptId`, `RulebookReference`, `AppealRevision`, `AuditNote`, `SourceReport`, `Measurements`, `JudgeScores`, `SourceDelivery`, `SourceReceivedEvent`, `AttemptState`, `WaitingAttemptState`, `SettledAttemptState`, `UnderAppealAttemptState`, `DisqualifiedAttemptState`, `AttemptStage`, `AttemptProgress`, `AuditTrail`, `RoundRepository`, `InMemoryRoundRepository`, `ReceiveResultUseCase`, `ReceiveResultCommand`, `Reception`, `GetAttemptBreakdownUseCase`, `AttemptBreakdown`, `AttemptController`, `AttemptDto`, `BreakdownDto`, `AttemptJpaEntity`, `AttemptMapper`, `JpaAttemptRepository`, `SpringDataAttempts`, `MetricsJson`.
- **Clases modificadas**: `Attempt`, `AttemptIdentity`, `AttemptRepository`, `InMemoryAttemptRepository`, `RawMetrics`, `EvaluationSnapshot`, `AttemptScoreSnapshot`, `ResultRegisteredEvent`, `ScoreAdjustedEvent`, `AttemptEvent`, `TeamScore`, `Round`, `Slot`, `ScheduleRoundUseCase`, `FileAppealUseCase`, `ResolveAppealUseCase`, `PublishOfficialRankingUseCase`, `AppealJpaEntity`, `AppealMapper`, `UseCaseConfig`, `InMemoryRepositoryConfig`, `DemoFixture`, `DemoConfig`.
- **Clases eliminadas**: `SlotReference`, `CaptureAttemptResultUseCase`, `CaptureAttemptResultCommand`.
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
- **Deuda que decidimos no resolver en este paso**:
  - `challengeId` viaja en el cuerpo de la captura hasta que la ronda conozca su desafío (frente 3); entonces sale de la ronda.
  - `RoundRepository` es mínimo y en memoria; el frente 3 lo extiende y el
    frente 5 coordina su persistencia.
  - No se valida el horario del turno ni su estado (`Slot.status`), y del panel de jueces solo se valida al juez que carga, no a cada juez que puntúa.
  - Descalificar solo se acepta sobre un intento puntuado, como el diagrama de estados.
  - La tabla sigue armándose con el mejor intento (`TeamScore`) y `TieBreakerChain`; cuando use `RankingScheme` (frente 4), cada `RoundScore` sale de `countableScore()` y su snapshot.
  - Las rutas de apelación (`POST /attempts/{id}/appeals`, aceptación y rechazo) son del frente 4; los casos de uso ya usan el intento nuevo.
  - La dependencia estática de reloj/UUID se resolvió en la sección 2.20; los
    ids de referencias se tiparon en la sección 2.21.

---

### 2.19 Plataforma: transacciones, JSON estricto y demo con fechas fijas

- **Una transacción por caso de uso (issue #13)**: un caso de uso que guarda dos agregados (aceptar una apelación guarda la apelación y el intento) podía dejar uno guardado y el otro no. `TransactionalUseCases` envuelve cada bean del paquete de casos de uso en un proxy transaccional desde el composition root, como un decorador: `roboleague-application` sigue sin Spring (regla #11) y ningún caso de uso cambió. Un guardado que Postgres rechaza al confirmar (versión vieja o id repetido) es 409.
  - *Alternativas descartadas*: `@Transactional` en los casos de uso (mete Spring en la aplicación); un puerto `Transactions` inyectado en cada caso de uso (cambia constructores de varios frentes para el mismo efecto); transacciones en los controllers (la demo, que llama a los casos de uso directo, quedaría afuera).
- **JSON estricto (issue #14)**: un campo desconocido es 400 con el nombre del campo y los esperados, en lugar de ignorarse (`"penalties"` en vez de `"deductions"` perdía la penalización). El reglamento acepta y descarta `version` y `requiredSources`, que trae cuando se lo lee con `GET`, para poder mandarlo de vuelta tal cual.
- **Demo con fechas fijas**: la edición es del 10 al 12/11/2026 y las rondas arrancan desde las 9:00 del primer día, una por hora, así cada corrida termina igual.
- **Actualización**: reloj e ids se inyectan como puertos (2.20). La spec 03
  resolvió el calendario de elegibilidad usando el inicio de cada edición (2.22).

---

### 2.20 Tiempo e identidades generadas como dependencias explícitas

- **Problema**: Las llamadas al reloj del sistema y a UUID en el núcleo hacían
  depender fixtures, auditoría y fechas del host y del momento de ejecución.
- **Solución**: Los puertos `com.roboleague.support.Clock` e `IdGenerator` viven
  en domain. `SystemClock` y `UuidGenerator` viven en infrastructure y se ensamblan
  en `UseCaseConfig`. La propiedad `roboleague.time-zone`, configurable con
  `ROBOLEAGUE_TIME_ZONE`, usa `America/Argentina/Buenos_Aires` por defecto.
  El núcleo no consulta estáticamente el reloj ni genera UUID por su cuenta.
- **Valores en los agregados**: Registration, Documentation, Appeal y Ranking reciben
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
  RankingId y AppealId en sus contextos. Se reutilizan ChallengeId y AttemptId,
  que ahora contiene SlotId y número positivo. Los valores rechazan null/blanco,
  conservan espacios y mayúsculas de valores válidos y no exigen UUID.
- **Alcance**: Entidades, profiles, scopes, commands, repositorios y claves de
  mapas de equipos y jueces usan los tipos. IdGenerator sigue entregando texto;
  quien genera un slot, ronda, ranking o apelación lo envuelve inmediatamente.
  El desempate final conserva el orden textual mediante Comparable<TeamId>.
  El filtro de ronda de Ranking y recálculo usa Optional<RoundId>: ausencia
  significa todas las rondas, evitando una identidad vacía ficticia.
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
- **Rehidratación**: Edition.restore valida pertenencia, categorías, fecha de
  referencia y unicidad, conservando los tiempos sin reevaluar equipos. Team
  admite construcción completa de su estado; Documentation.restore conserva
  documentos y verificación sin replay. Para frente 5, la restricción única de
  Registration es edición/equipo, y los mappers deben preservar estos valores.
- **Alternativas**: No se agregó un cierre de inscripción, pues no existe ese
  ciclo de vida. Copias defensivas de un Team mutable habrían conservado múltiples
  fuentes de estado; bloquear todos los cambios impediría actualizaciones válidas.
  Se eligieron snapshots inmutables con validación coordinada de inscripciones.
- **Límites**: Team y Edition siguen en memoria. Validar antes de escribir evita
  cambios parciales por rechazo, pero no rollback ante fallos técnicos ni control
  de concurrencia entre varios escritores. Repositorios/restore son mecanismos
  confiables de persistencia, no entradas de negocio. JPA, restricciones únicas
  y coordinación de escrituras concurrentes corresponden al frente 5. Endpoints
  de inscripción/cambios/programación corresponden a la spec 05.
- **Verificación**: Calendario con edades que cambian elegibilidad al iniciar la
  edición, rechazos sin escrituras, duplicados, relaciones independientes,
  actualizaciones válidas e inválidas contra dos categorías, inmutabilidad y
  rehidratación, revalidación al programar, rankings canónicos, proxies y demo.

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
| **Composition Root en API (configuración Spring)** | Núcleo libre de frameworks; puertos internos y adaptadores seleccionados externamente. | Mantener registros de beans, selección de repositorios y restricciones de proxies transaccionales. | Crear adaptadores tecnológicos dentro del negocio. Rechazada por acoplamiento indebido. |
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

### Evolución de decisiones técnicas inicialmente pospuestas:
1. **Framework de Persistencia Real (JPA / Hibernate / Spring Data)**:
   - *Entrega 1*: Se difirió la persistencia real porque la consigna se centraba en el módulo de dominio.
   - *Estado actual*: infrastructure incorpora Spring Data JPA, Hibernate, Postgres y migraciones Flyway para `Appeal`, `Challenge` y `Attempt`. `Edition`, `Team`, `Round` y `Ranking` siguen en memoria. Las entidades JPA y mappers son propios del adaptador; domain y application permanecen libres de anotaciones de persistencia. Flyway administra el esquema y Hibernate lo valida.
2. **Contenedor de Inyección de Dependencias Automatizado (Spring / Guice / CDI)**:
   - *Entrega 1*: Se utilizó ensamblado manual en `Main` y fixtures de test.
   - *Estado actual*: Spring Boot ensambla la aplicación desde API; `UseCaseConfig` registra casos de uso y servicios, y `TransactionalUseCases` aplica las transacciones. El núcleo conserva inyección por constructor y Java sin anotaciones Spring. La independencia del dominio se mantiene aunque el ensamblado externo use un framework.
3. **Motor de Reglas Externo con DSL (Drools / Rete)**:
   - *Decisión*: Resolver el scoring mediante composición de objetos Java puros (`ScoreRule`).
   - *Justificación*: Strategy + Composite permite componer y probar reglas Java sin un motor externo. Las definiciones configurables se validan y reconstruyen mediante `RuleCatalog`; eso incluye procesamiento en ejecución. No se presupone ausencia de parsing ni superioridad de rendimiento sin mediciones.

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
  - `MetricDefinitionTest` y `MeasurementCheckTest`: con casos parametrizados, que una medición se acepta solo entera si es un conteo y dentro de su rango, y que revisar lo que manda una fuente junta todos los problemas (faltante, mal escrita, de otra fuente, fuera de rango) sin exigir las métricas de la otra fuente. `RuleCatalogTest` suma la ida y vuelta de las métricas declaradas y los rechazos por rango inválido, métrica repetida, no declarada o de otra fuente.
  - `ScoreRuleSectionsTest`: con casos parametrizados por regla y carrera (perfecta, media, mala), que una regla base o una bonificación nunca resta y una deducción nunca suma. `RuleCatalogTest` prueba cada tipo en la lista de su sección y rechaza un tipo en la sección equivocada o con parámetros desconocidos; `RankingSchemeTest` desempata un Seguidor de línea real por menor descuento.
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
