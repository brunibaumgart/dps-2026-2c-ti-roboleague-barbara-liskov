# Documento de Decisiones de Diseño (DESIGN.md) - RoboLeague

**Trabajo Integrador - Entrega 1: Módulo de Dominio**  
**Plataforma de Competencias de Robótica y Desafíos Técnicos**

---

## 1. Modelo de Dominio y Bounded Contexts

El dominio del sistema se estructura en 4 submódulos o Bounded Contexts conceptuales bien definidos para garantizar una alta cohesión y bajo acoplamiento:

```text
roboleague-domain/
├── tournament/          # Eventos, Ediciones, Categorías, Equipos, Elegibilidad
├── scheduling/          # Rondas, Turnos (Slots), Pistas, Jueces
├── evaluation/          # Intentos, Métricas capturadas, Reglas de Scoring, Auditoría
└── ranking/             # Criterios de ordenamiento, Desempates, Publicación, Apelaciones
```

### Contextos Delimitados:
1. **`tournament`**: Administra la estructura organizativa de las competencias (temporadas, torneos, ediciones anuales o periódicas), categorías técnicas con sus restricciones físicas/etarias, registro de equipos con integrantes y robots, y la verificación compuesta de elegibilidad.
2. **`scheduling`**: Gestiona la logística operativa: pistas/arenas de prueba, jueces oficiales, rondas (clasificatorias, finales) y asignación ordenada de turnos (slots).
3. **`evaluation`**: Núcleo computacional del scoring. Modela los intentos en pista (`Attempt`), la captura de métricas brutas (`RawMetrics`), el motor de puntuación explicable y versionado (`ScoringPolicy`, `ScoreRule`, `ScoreBreakdown`), y la trazabilidad append-only mediante revisiones y eventos de dominio.
4. **`ranking`**: Consolida los puntajes de los equipos, aplica cadenas de resolución de desempates desacopladas (`TieBreakerChain`), administra los estados de la tabla (`PROVISIONAL` vs `OFFICIAL`) y gobierna el ciclo de vida de apelaciones y reclamos (`Appeal`) mediante una máquina de estados.

---

## 2. Decisiones de Diseño Principales y Patrones Aplicados

### A. Motor de Puntuación Explicable y Versionado (Strategy + Composite + Specification)

- **Patrones Aplicados**:
  - **Strategy**: La interfaz `ScoreRule` desacopla el algoritmo de cálculo de cada tipo de desafío del motor central.
  - **Composite**: `CompositeScoreRule` permite componer jerárquicamente múltiples reglas (`TimeBasedRule`, `ObjectiveBonusRule`, `PenaltyRule`, `JudgeSubjectiveRule`, `ResourceConsumptionRule`) tratando a una regla individual y al conjunto de reglas de manera polimórfica y uniforme.
  - **Value Objects Inmutables (`ScoreBreakdown` & `ScoreItem`)**: Al evaluar un intento en pista (`Attempt`), la regla no devuelve un número escalar primitivo (`double`), sino un desglose inmutable que contiene:
    - Concepto evaluado (`concept`).
    - Métrica bruta observada (`rawMetric`).
    - Fórmula matemática aplicada (`appliedFormula`).
    - Subtotal computado (`subtotal`).
    - Notas y penalizaciones aplicadas con sus justificaciones.
    - Puntaje final consolidado (`totalScore`).
  - **Inmutabilidad del Reglamento (`ScoringPolicy`) por Edición**: Cada `Edition` se enlaza con una instancia inmutable y versionada del reglamento (ej. `"v1.0.2026"`). Esto asegura que recálculos futuros de ediciones pasadas utilicen con total precisión las reglas vigentes en aquel momento histórico, respetando el principio Abierto/Cerrado (OCP).

### B. Registro de Resultados y Auditoría (Append-Only Log / Event Sourcing Ligero)

- **Patrón Aplicado**: **Append-Only Log con Snapshots Inmutables y Eventos de Dominio**.
- **Dónde y Por qué**:
  - En la entidad aggregate `Attempt`.
  - La consigna exige: *"Los resultados nunca se sobreescriben directamente. Conservar los valores originales y todas las modificaciones."*
  - Cada modificación (registro inicial de resultados, penalizaciones post-revisión de video, ajustes tras apelación concedida) crea una nueva revisión inmutable (`AttemptScoreSnapshot`) identificada con número incremental de revisión, timestamp, autor o juez responsable, métricas capturadas, desglose resultante y motivo del cambio.
  - Asimismo, se emiten y preservan eventos de dominio inmutables (`ResultRegisteredEvent`, `PenaltyAppliedEvent`, `AppealAcceptedEvent`, `ScoreAdjustedEvent`).
  - Esto garantiza auditoría forense inmediata y habilita el recálculo determinista reejecutando las reglas sobre cualquier punto de la historia.

### C. Ranking y Desempates (Comparator Composition / Chain of Responsibility)

- **Patrón Aplicado**: **Comparator Composition (Composición Funcional de Criterios)**.
- **Dónde y Por qué**:
  - En `TieBreakerChain`, implementando `Comparator<TeamScore>`.
  - Permite encadenar dinámicamente criterios de ordenamiento sin acoplar la lógica de ranking a una única fórmula fija:
    1. Mayor puntaje total consolidado (`HIGHEST_TOTAL_SCORE`).
    2. Menor tiempo de recorrido en pista (`LOWEST_TIME_TAKEN`).
    3. Menor cantidad de penalizaciones acumuladas (`LOWEST_PENALTIES`).
    4. Mayor puntuación otorgada por los jueces evaluadores (`HIGHEST_JUDGE_SCORE`).
    5. Desempate determinista por identificador de equipo en caso de igualdad estricta.
  - Publicación y Estados: `Ranking` diferencia explícitamente entre `RankingStatus.PROVISIONAL` y `RankingStatus.OFFICIAL`. Se bloquea la publicación del ranking oficial si existen apelaciones pendientes o en revisión.

### D. Elegibilidad y Apelaciones (Specification y State)

- **Patrón Specification**:
  - `EligibilitySpecification<T>` con operaciones booleanas combinables (`and`, `or`, `not`).
  - Especificaciones concretas:
    - `AgeLimitSpecification`: valida rangos etarios de todos los integrantes respecto a la fecha del torneo.
    - `TeamSizeSpecification`: valida límites mínimo y máximo de integrantes.
    - `RobotSpecificationLimit`: valida que el peso (g) y dimensiones (largo, ancho, alto en mm) no superen los topes de la categoría.
    - `DocumentationVerifiedSpecification`: valida que la ficha técnica y consentimientos estén aprobados.
  - Devuelve un `EligibilityResult` enriquecido que detalla las causales de rechazo en lenguaje de dominio.
- **Patrón State**:
  - En la entidad `Appeal` para gobernar su ciclo de vida:
    - `PendingAppealState`: Estado inicial al presentar el reclamo. Bloquea aceptación directa sin revisión previa.
    - `UnderReviewAppealState`: Asignado a un árbitro o juez de apelaciones para su evaluación.
    - `AcceptedAppealState`: Estado final de resolución favorable. Requiere métricas revisadas, actualiza el historial auditado del intento y dispara automáticamente `RecalculateRankingUseCase`.
    - `RejectedAppealState`: Estado final de resolución desestimatoria con fundamentos.

---

## 3. Matriz de Pros y Contras del Diseño

| Decisión Arquitectónica | Pros | Contras |
| :--- | :--- | :--- |
| **`ScoreBreakdown` como Value Object Explicable** | Transparencia absoluta ante reclamos y auditoría; testeabilidad directa de cada subtotal; desacopla la visualización de la matemática. | Mayor sobrecarga de instanciación de objetos en memoria en comparación con un primitivo `double`. |
| **Reglas de Scoring como Strategy + Composite** | Cumplimiento estricto de OCP: agregar nuevas modalidades de competencia (consumo de energía, hitos de IA) no requiere modificar el núcleo. | Requiere que `RawMetrics` transporte las variables necesarias para todas las reglas activas o valide su presencia. |
| **Inmutabilidad del Reglamento por Edición** | Reproducibilidad histórica garantizada. Modificaciones futuras del reglamento jamás rompen torneos pasados. | Requiere duplicar o clonar plantillas de reglas al crear nuevas ediciones. |
| **Append-Only / Snapshots para Auditoría** | Trazabilidad completa; preservación de valores originales y revisiones; cumplimiento directo de la consigna. | Mayor complejidad de modelado respecto a un CRUD convencional con columnas `updated_at`. |
| **Desempates por Composición Funcional (`Comparator`)** | Máxima flexibilidad para reordenar prioridades por categoría; testing aislado de cada comparador. | Si un desempate dependiera de métricas externas no incluidas en `TeamScore`, requeriría extender el modelo de datos. |
| **Máquina de Estados (`State`) para Apelaciones** | Impide transiciones ilegales de negocio (ej. aceptar un reclamo sin revisión previa o modificar una apelación cerrada). | Mayor cantidad de clases e interfaces pequeñas que un enum con `switch/case`. |

---

## 4. Alternativas Descartadas y Patrones No Aplicados

### Alternativas Descartadas:
1. **Puntajes calculados como simple escalar `double`**:
   - *Descartada porque*: Un valor numérico simple no es auditable ni explicable. Ante una apelación, resulta imposible justificar cómo se distribuyeron las bonificaciones por tiempo, objetivos o penalizaciones.
2. **Sobreescritura directa del resultado (`attempt.setScore(newScore)`)**:
   - *Descartada porque*: Destruye el rastro de auditoría exigido en la consigna. No permitiría verificar si una apelación alteró legítimamente un resultado ni inspeccionar los datos originales.
3. **Reglas de scoring acopladas con `switch-case` dentro de `Attempt` o `Edition`**:
   - *Descartada porque*: Violación flagrante del principio de Responsabilidad Única (SRP) y Abierto/Cerrado (OCP), haciendo imposible que nuevas categorías agreguen reglas sin modificar código existente.

### Patrones Decididos No Aplicar y sus Consecuencias:
1. **Event Sourcing Completo con Framework (ej. Axon / EventStore)**:
   - *Decisión*: No aplicar un framework de Event Sourcing completo con snapshots distribuidos o serialización binaria compleja.
   - *Consecuencia*: Se implementó un modelo ligero in-memory de append-only log (`List<AttemptScoreSnapshot>` y `List<AttemptEvent>`) perfectamente adecuado para el módulo de dominio sin sobrecargar el proyecto de dependencias de infraestructura.
2. **Reglas mediante DSL externo o motor de reglas pesadas (ej. Drools)**:
   - *Decisión*: No incorporar un motor de reglas externo basado en Rete o archivos `.drl`.
   - *Consecuencia*: Mantiene el código 100% tipado en Java nativo, compilable a alta velocidad en Java 25 y testeable de forma pura sin dependencias pesadas de terceros.
3. **Persistencia ORM / Base de Datos Real (Hibernate / JPA / Spring Data)**:
   - *Decisión*: No aplicar frameworks de persistencia real en esta entrega (conforme a las instrucciones expresas del enunciado: *"No se exige API REST, persistencia real, frontend, seguridad ni despliegue. Únicamente módulo del dominio"*).
   - *Consecuencia*: Las entidades del dominio y los repositorios se mantienen desacoplados y puros, respaldados por implementaciones concurrentes en memoria para la suite de pruebas unitarias y de integración.
