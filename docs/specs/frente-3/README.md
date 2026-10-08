# Frente 3: inscripción, programación y front

Estado: specs 01–03 implementadas y verificadas; specs 04–06 pendientes.

Fuente: **Hoja de ruta RoboLeague.html**, sección «Frente 3», hallazgos #6 y #7,
higiene de reloj/ids y flujo «RoboLeague en seis pasos». Se contrastó con el
repositorio al redactar. El HTML contiene propuestas y ejemplos históricos:
sus fragmentos de código no son instrucciones de ejecución ni una descripción
garantizada de lo que hoy existe. Los plazos de septiembre/octubre del documento
no se toman como fechas de este plan.

## Orden de implementación

| Orden | Spec | Resultado |
| --- | --- | --- |
| 1 | [01-clock-and-generated-identifiers.md](01-clock-and-generated-identifiers.md) | Tiempo y generación de ids controlables en pruebas |
| 2 | [02-typed-identities.md](02-typed-identities.md) | Identidades distintas para conceptos distintos, sin cambiar el JSON existente |
| 3 | [03-registration-and-eligibility.md](03-registration-and-eligibility.md) | Registration y cambios de equipo que preservan elegibilidad |
| 4 | [04-rounds-and-parallel-scheduling.md](04-rounds-and-parallel-scheduling.md) | Rondas por desafío y turnos sin conflictos de recursos |
| 5 | [05-registration-and-scheduling-api.md](05-registration-and-scheduling-api.md) | Contratos HTTP y consultas necesarios para operar estos flujos |
| 6 | [06-web-frontend-and-integration.md](06-web-frontend-and-integration.md) | Front visual de los flujos y comprobación de integración |

Cada spec define comportamiento, límites y criterios de aceptación. Implementar
una por pedido, incluyendo sus pruebas, adaptación de consumidores y documentación.
No comenzar la siguiente automáticamente. Las decisiones propuestas aquí son
el diseño de implementación de este plan; no se atribuyen a la consigna original.
Antes de implementar, revisar cambios recientes de los compañeros y conservar
contratos equivalentes que ya hayan integrado.

## Alcance y coordinación

- Frente 3: contratos de reloj/ids, inscripción, programación, API propia y front.
- Frentes 1/2: reglamentos, captura y evaluación. Adaptar sus consumidores solo
  cuando una firma compartida cambie; no reescribir motores de scoring.
- Frente 4: tablas versionadas, publicación y ciclo completo de apelaciones.
  El front consume sus contratos; no implementar esas reglas en JavaScript.
- Frente 5: adaptadores JPA y plataforma. Los nuevos modelos/puertos de este
  frente deben tener adaptadores en memoria y puntos de rehidratación coherentes;
  su persistencia Postgres se coordina con frente 5. Si falta, explicitar que los
  datos se pierden al reiniciar y que no hay rollback en memoria. No declarar
  cumplida la entrega completa de persistencia por haber terminado estas specs.

El hallazgo #7 dice que faltaba RoundRepository; hoy existe con `save` y
`findBySlotId`. La spec 04 lo extiende, no crea un segundo puerto.
Hoy la API implementa configuración de ediciones/desafíos, capturas/desglose y
revisión de apelaciones. Inscripción/programación y varias operaciones de
apelaciones/tablas siguen sin endpoint. La spec 06 identifica estas dependencias
como tales: no simular respuestas exitosas para ocultar lo pendiente.

## Reglas comunes

Seguir los `AGENTS.md` de raíz y módulos. Mantener domain/application sin
frameworks, invariantes en domain, coordinación en application y traducciones
en adaptadores. Preservar ids textuales y referencias persistidas, historial y
versión de reglamentos. Migraciones nuevas cuando se requiera cambiar esquema;
no editar las ya integradas. No eliminar VOs solo por ser records: cualquier
simplificación exige identificar el concepto y sus consumidores y justificarla.

Verificación: `.agents/skills/verify-change/SKILL.md`. Cambios transversales de
01/02/03/04 requieren comprobar consumidores y suite completa con Docker; tests
en memoria no demuestran atomicidad ni concurrencia de Postgres. Actualizar
README/DESIGN y las descripciones de estado en AGENTS al cambiar la implementación.
