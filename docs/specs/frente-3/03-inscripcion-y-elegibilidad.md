# 03 — Registration y elegibilidad sostenida

Estado: pendiente. Depende de 01 y 02. Cierra hallazgo #6.

## Estado actual

`RegisterTeamUseCase` valida una especificación configurada con fecha del sistema,
guarda Team y agrega el mismo objeto mutable a Edition. Team permite cambiar
categoría, robot e integrantes; Robot y Documentation también son mutables.
Es posible invalidar una inscripción sin volver a validar. La categoría del
equipo y la relación con la edición están mezcladas.

## Modelo propuesto

Introducir `Registration` como entidad de inscripción dentro de `Edition`, con
TeamId, EditionId, CategoryId, fecha de referencia y momento de inscripción.
Su identidad es la combinación edición/equipo: un equipo tiene una inscripción
en una categoría por edición. La fecha de referencia es `Edition.startDate`.
Es una decisión del plan para concretar «fecha de la edición» del HTML.

`TeamRepository` es la fuente canónica del equipo. Edition conserva relaciones
Registration, no copias ni referencias mutables de Team. La categoría competitiva
pertenece a Registration, permitiendo categorías distintas entre ediciones.
Adaptar especificaciones para evaluar un candidato junto con categoría y fecha
explícitas, manteniendo composición y motivos detallados.

La inscripción comprueba categoría ofrecida por esa edición, edad a su fecha
de inicio, cantidad de miembros, límites del robot y documentación verificada.
Inscripciones repetidas se rechazan como conflicto. Datos inválidos no dejan
Team nuevo ni Registration parciales.

### Cambios de equipo

Propuesta elegida: permitir cambios válidos mediante una operación controlada;
no introducir un cierre de inscripción porque hoy no existe ese ciclo de vida.
Construir un candidato completo sin mutar el equipo original y validar contra
**todas** sus inscripciones antes de reemplazar el equipo canónico. Obtener esas
inscripciones consultando ediciones por TeamId (puerto existente extendido o
consulta sobre `findAll` en el adaptador en memoria).

Si se cambia categoría, hacerlo sobre la Registration de una edición concreta,
verificando que la edición la ofrezca y el equipo sea elegible para ella. Un
rechazo conserva equipo e inscripciones anteriores. No aceptar un id ya existente
para sobrescribir su equipo mediante otro pedido de inscripción.

No exponer mutadores directos ni objetos Robot/Documentation mutables que
permitan saltar estas reglas. Preferir estado de equipo reemplazable e inmutable,
fábricas/operaciones que construyan candidatos y consultas de solo lectura.
Toda vía pública que cambie miembros, robot o documentación debe pasar por el
mismo control de inscripciones. Al programar una ronda, revalidar la elegibilidad
para no confiar en datos obsoletos introducidos por otro adaptador.

## Integración

Migrar `RegisterTeamUseCase` y agregar un caso de uso de actualización controlada.
Adaptar scheduler, ranking/recalculación y fixtures que usan `Edition.getTeamsByCategory`:
resolver TeamIds de las registrations contra TeamRepository. No mantener una
lista paralela de equipos solo para conservar la firma antigua.
Mantener los cálculos de ranking del frente 4, cambiando únicamente el modo de
obtener participantes. Rehidratación conserva relaciones y tiempos.

Esta spec incluye dominio, casos de uso y adaptadores memory. JPA de Team/Edition
corresponde al frente 5: documentar el modelo para sus mappers y no fingir
rollback en memoria. Verificar antes de guardar y evitar aliases mutables.
HTTP se implementa en 05.

## Criterios de aceptación

1. Una edad elegible hoy pero inelegible al inicio de la edición se rechaza,
   y el caso inverso se acepta cuando cumple las demás reglas.
2. Rechazar categoría ajena, cero/exceso de miembros, robot fuera de límites
   y documentación no verificada; devolver todos los motivos pertinentes.
3. Reinscribir el mismo equipo en la misma edición falla sin duplicados;
   inscribirlo en otra edición crea una relación independiente.
4. Quitar el último integrante o cambiar robot/categoría invalidando cualquier
   inscripción falla y deja el estado anterior intacto, incluso en memoria.
5. Un cambio válido actualiza el equipo consultado desde todas sus inscripciones.
   Dos ediciones con restricciones diferentes se validan independientemente.
6. No hay vía pública que modifique equipo/robot/documentación registrados
   eludiendo el control. Los listados no exponen referencias mutables.
7. Tests de inscripción, programación, ranking y demo mantienen sus flujos.

## Fuera de alcance

Cierre de inscripción, bajas/cancelaciones, autenticación de organizadores,
upload de documentos, historial versionado de equipos y modificación de fechas
o restricciones de una edición ya utilizada. Si otro frente permite esta última,
coordinar revalidación antes de aceptar el cambio.
