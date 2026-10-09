# 05 — API de inscripción y programación

Estado: implementada y verificada. Depende de 03 y 04 (y de sus bases 01/02).

## Estado previo y objetivo

Existe POST /editions y API de desafíos/capturas, pero no controllers para
RegisterTeamUseCase o ScheduleRoundUseCase. Exponer estos flujos y consultas
para que el front pueda elegir datos sin conocer fixtures ni introducir ids a ciegas.

## Contratos HTTP propuestos

Todos los ids viajan como strings; DTOs propios y ErrorDto existente. Registrar
casos de uso de consulta además de mutación; controllers no consultan adaptadores
directamente. Mantener código de ids inexistentes en 400 según convención actual.

| Operación | Ruta | Éxito |
| --- | --- | --- |
| Listar ediciones | GET /editions | 200, lista (vacía si no hay) |
| Detalle de edición y categorías/restricciones | GET /editions/{editionId} | 200 |
| Listar desafíos de edición | GET /editions/{editionId}/challenges | 200 |
| Crear inscripción | POST /editions/{editionId}/registrations | 201 |
| Listar inscripciones | GET /editions/{editionId}/registrations | 200 |
| Consultar inscripción | GET /editions/{editionId}/registrations/{teamId} | 200 |
| Actualizar candidato completo de equipo/categoría | PUT /editions/{editionId}/registrations/{teamId} | 200 |
| Programar ronda | POST /challenges/{challengeId}/rounds | 201 |
| Listar rondas, filtro opcional categoryId | GET /challenges/{challengeId}/rounds | 200 |
| Consultar ronda y slots | GET /rounds/{roundId} | 200 |

### Inscripción

Body: categoryId y datos completos del equipo: id, name, institution, miembros
(id, fullName, birthDate, role), robot (id, name, peso/dimensiones/hardware) y
documentación (referencias y evidencia de verificación: autor). El momento de
verificación/inscripción lo pone el servidor con el reloj de 01; no aceptar
timestamps enviados para eludir las reglas. No crear upload o autenticación.

POST puede referenciar un equipo existente por teamId en lugar del objeto team,
pero exige exactamente una de las dos alternativas. Un id existente en el objeto
team no habilita sobrescritura. PUT requiere candidato completo, id del path
coherente e inscripción existente; aplica la revalidación de 03 en todas las ediciones.

Respuesta de inscripción: editionId, teamId, categoryId, registeredAt,
referenceDate y vista del equipo. Las categorías exponen las restricciones
necesarias para orientar el formulario, incluyendo dimensiones si el modelo
las configura, sin reconstruir reglas de elegibilidad en JavaScript.

### Programación

Body: categoryId, roundNumber, roundName, startTime, slotDurationSeconds,
intervalSeconds, pistas (id/name/surfaceType/isActive) y jueces
(id/fullName/specialty). La edición se deriva del desafío, no de un id contradictorio.
No hay catálogos persistidos de pistas/jueces hoy: se envían aquí y el front
puede sugerir recursos consultados de rondas anteriores. No inventar repositorios
de esos recursos como requisito de esta spec.

Respuesta: roundId, challengeId, editionId, categoryId, roundNumber, name,
status y slots con slotId, teamId, pista, jueces, inicio/fin y estado.
La consulta conserva orden de horarios, pista y slotId para una presentación estable.

## Errores e integración

- JSON mal formado, propiedades desconocidas, campos obligatorios faltantes,
  identidades inválidas/referencias inexistentes → 400.
- Inscripción o número de ronda duplicados/transiciones no permitidas → 409.
- Candidato inelegible → 422 con motivos en details; ninguna mutación parcial.
- Conflictos de escritura concurrente de Postgres → 409, según handler actual.

Usar beans de `UseCaseConfig` y preservar proxies de `TransactionalUseCases`.
La configuración de creación de ediciones ya expone solo peso máximo en su body:
si se requiere configurar dimensiones reales para inscripción, ampliar ese
contrato de forma compatible con campos opcionales, sin quitar el default existente.
Documentar JSON exacto elegido, unidades, defaults y ejemplos en README.

## Criterios de aceptación

1. Tests MockMvc demuestran crear/consultar/actualizar inscripción y programar/
   consultar ronda con contratos de DTO y códigos indicados.
2. Casos de 400/409/422 conservan equipo, inscripción y ronda anteriores.
3. El equipo listado refleja cambios válidos; categorías y desafíos no se
   mezclan entre ediciones. Las consultas no producen escrituras.
4. Slots devueltos sirven para los endpoints actuales de measurements y
   judge-scores, y el desglose conserva sus contratos.
5. Listas vacías son 200; filtros de categoría inválidos se distinguen de una
   categoría válida sin rondas. No se devuelven entidades JPA/agregados directamente.
6. IDs de tests no colisionan en ApiTest compartido y la demo sigue iniciando.

## Fuera de alcance

Endpoints de tablas, presentación/aceptación/rechazo de apelaciones (frente 4),
autorización, administración completa de recursos y persistencia pendiente de
otros frentes. Reusar rutas equivalentes de compañeros antes de agregar duplicados.

## Implementación realizada

- Se implementaron las diez operaciones propuestas con DTOs propios y consultas
  de application. Contratos exactos, unidades, defaults y ejemplos en README.
- EditionDto conserva categories (ids) y agrega categoryDetails (restricciones).
  Alta de categorías admite maxLengthMm/maxWidthMm/maxHeightMm opcionales,
  default 1000 mm por eje. Cantidades físicas deben ser positivas y finitas.
- Registro admite team completo o teamId, exactamente uno. TeamBody recibe
  robot con weightGrams/lengthMm/widthMm/heightMm/actuatorCount/sensors y
  documentación con documents/verifiedBy. El servidor fija tiempos. Metadatos
  de respuesta enviados en el request son campos desconocidos (400).
- PUT usa UpdateRegistrationUseCase para validar equipo/categoría juntos antes
  de guardar, revalidando todas las otras inscripciones y conservando las fechas.
  Una transición conjunta válida no se evalúa contra la categoría reemplazada.
- Consultas muestran Team canónico, filtran desafíos por edición en ambos
  adaptadores y distinguen filtros inválidos de listas vacías. No escriben.
- Programación deriva edición del desafío. RoundDto muestra slots por horario,
  TrackId y SlotId, un juez asignado y pausa de pista en segundos. Slots sirven
  para measurements/judge-scores y breakdown existentes.
- Pruebas cubren 400/409/422 sin cambios parciales por rechazo, consultas,
  programación/captura, revalidación entre ediciones, puertos y proxies.
  Verificación transversal: `mvn -B -o verify` con Postgres/Testcontainers.
- Team/Edition/Round permanecen en memoria; no se implementó JPA ni locking
  fuera del alcance acordado con frente 5. La siguiente spec es 06.
