# 04 — Rondas por desafío y programación de recursos

Estado: pendiente. Depende de 01, 02 y 03. Cierra hallazgo #7; aporta contrato
de ronda/slot para captura (frente 2) y clasificación (frente 4).

## Estado actual

Round y RoundRepository ya existen; el puerto tiene `save` y `findBySlotId`.
RoundScope identifica edición/categoría/número, sin desafío. El scheduler rota
pistas pero usa un único horario, serializando todos los turnos. También asigna
dos jueces cuando hay varios, sin comprobar solapamientos. Round y Slot exponen
mutadores sin controlar pertenencia o transiciones.

## Modelo y contratos

- Round es raíz del agregado; contiene slots, scope con ChallengeId, EditionId,
  CategoryId y número positivo, y estado. Identidad del slot única dentro y fuera
  de la ronda. El desafío debe pertenecer a la edición y la categoría estar ofrecida.
- Programar para inscripciones elegibles de la categoría; un slot por equipo
  por ronda. Rechazar scope duplicado desafío/categoría/número antes de guardar.
- Los slots solo cambian a través de operaciones de Round. No permitir añadir
  un slot de otra ronda, duplicar identidad ni incorporar solapamientos del
  mismo equipo, pista o juez. Consultas entregan datos protegidos de mutación.
- Transiciones propuestas: Round SCHEDULED → IN_PROGRESS → COMPLETED; Slot
  SCHEDULED → IN_PROGRESS → COMPLETED y SCHEDULED → CANCELLED. Rechazar
  transiciones inversas y terminales. Completar ronda exige todos los slots
  completados o cancelados. Esta regla de lifecycle no equivale a «todas las
  fuentes de medición recibidas», que sigue siendo responsabilidad del frente 2.
- Extender RoundRepository con consulta por RoundId, por scope/desafío y
  `findBySlotId(SlotId)`, conservando un solo puerto. Guardar/rehidratar el agregado
  completo; no crear SlotRepository independiente.

## Algoritmo propuesto

Usar disponibilidad separada por pista y juez, comenzando en `roundStart`.
Para cada equipo, en orden estable de inscripción (desempate por TeamId), elegir
la combinación pista/juez con menor inicio posible; desempatar por orden de los
recursos suministrados. Asignar **un juez por turno** como mínimo del plan; no
replicar automáticamente el panel de mediciones como asignación de dos jueces.

Inicio = máximo de inicio de ronda y disponibilidad de la pista y juez elegidos.
Fin = inicio + duración. La pista vuelve a estar disponible en fin + intervalo;
el juez en fin (el intervalo es pausa de pista). Intervalos de ocupación
semicerrados `[inicio, fin)`, permitiendo que un recurso pase a otro turno al fin.
Excluir pistas inactivas y rechazar recursos duplicados o sin juez/pista disponible.
Duración positiva e intervalo no negativo, como hoy.

Dos pistas y dos jueces disponibles permiten los dos primeros slots en paralelo;
dos pistas y un solo juez no permiten usarlo simultáneamente. Comprobar además
ocupaciones ya guardadas de otras rondas, por pista, juez y equipo: adelantarlas
al fin del conflicto hasta encontrar una ventana libre. Recursos se identifican
por id aunque sus objetos hayan sido construidos en otro request.

Ejemplo: inicio 10:00, duración 5 min, pausa 1 min, equipos A/B/C/D, pistas P1/P2
y jueces J1/J2: A/B 10:00–10:05; C/D 10:06–10:11.
El mismo caso con J1 único: A 10:00–10:05 y B 10:05–10:10 en la otra pista.

## Integración y límites

ScheduleRoundCommand incorpora desafío; el caso de uso carga Challenge, Edition
y equipos/registrations, y genera RoundId/SlotIds con el puerto. Adaptar la demo.
ReceiveResultUseCase debe comprobar que el challenge recibido coincide con el
del slot/ronda, incluso en la **primera** captura; conservar equipo del slot y
comprobación de juez, sin alterar scoring o auditoría del frente 2.

Evitar solapamientos sobre el estado almacenado no garantiza exclusión de dos
programaciones concurrentes en memoria. Coordinar locking/transacciones de JPA
con frente 5 si se requiere esa garantía; documentar el límite y no presentarlo
como resuelto por un test secuencial. Esta spec incluye el adaptador memory,
restore y contrato para persistencia; no agrega JPA por fuera de ese acuerdo.

## Criterios de aceptación

1. Los ejemplos de horarios se reproducen con ids deterministas.
2. Ninguna pista, juez o equipo tiene ventanas solapadas, incluidas otras rondas.
3. Recursos inválidos, categoría ajena, challenge de otra edición, inscripción
   inelegible y scope duplicado se rechazan sin guardar una ronda parcial.
4. Guardar/cargar preserva ids, scope, slots, jueces, horarios y estados;
   `findBySlotId` localiza su ronda y no duplica slots al guardar de nuevo.
5. Intentar capturar con challenge ajeno, slot inexistente o juez no asignado
   falla sin crear ni modificar intento.
6. Operaciones del agregado rechazan slots ajenos y transiciones inválidas.
7. Consumidores de captura y ranking siguen compilando; no se implementa aquí
   agregación N de M, tablas versionadas ni guardas de publicación.
