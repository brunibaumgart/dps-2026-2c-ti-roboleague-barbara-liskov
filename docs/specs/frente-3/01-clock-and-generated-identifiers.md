# 01 — Tiempo e identificadores generados controlables

Estado: implementada. Dependencias: ninguna spec previa. Alcance: higiene compartida
del frente 3; tocar otros frentes únicamente para inyectar tiempo/ids.

## Problema actual

Hay `LocalDate.now()`, `LocalDateTime.now()` y `UUID.randomUUID()` en Team,
TeamMember, Documentation, AgeLimitSpecification, Ranking, Appeal y su estado
UnderReview, EventMetadata, AuditAuthor, RoundSchedulerService y casos de uso
ScheduleRound, FileAppeal y RecalculateRanking. SnapshotMetadata consume
AuditAuthor.now. El ensamblado de elegibilidad también usa la fecha del sistema.
Las pruebas no controlan completamente fechas ni identificadores generados.

## Diseño propuesto

Definir puertos pequeños en domain, en un paquete de soporte común:

- `Clock`: obtener la fecha/hora actual de la aplicación y su fecha de calendario.
  Evitar ambigüedad con `java.time.Clock` mediante imports explícitos. Mantener
  `LocalDateTime` y `LocalDate` donde ya son contratos, sin migrar a UTC/Instant
  ni cambiar columnas dentro de esta spec.
- `IdGenerator`: generar un identificador textual opaco. No validar como UUID
  todos los ids: existen ids de fixtures y suministrados por el cliente.

Adaptadores `SystemClock` y `UuidGenerator` en infrastructure; beans en API.
La zona del reloj de producción es explícita y configurable; default del plan:
`America/Argentina/Buenos_Aires`. No depender de la zona implícita del host.
Tests usan reloj fijo y secuencia determinista, sin cambiar reloj global.

Los casos de uso y servicios que necesitan tiempo/ids reciben los puertos por
constructor. Los agregados reciben valores explícitos de fecha, hora o metadatos
de la operación; no guardan servicios tecnológicos para funcionar.
Crear primero los metadatos y pasar esos valores a las transiciones/fábricas.
Conservar el orden y contenido de eventos y revisiones existentes.

`AttemptId` sigue derivando de slot/número y snapshot ids pueden seguir derivando
del intento/revisión; no reemplazarlos por ids aleatorios. Solo generar donde
hay identidad realmente nueva. La elegibilidad por edad se calculará con la
fecha de la edición (spec 03), no con el reloj. Para rechazo de nacimiento futuro,
pasar una fecha de validación explícita a la creación del participante; una
rehidratación no valida un dato histórico contra «hoy».

Los helpers sin argumentos que llaman al sistema deben migrarse o eliminarse.
No mantener una ruta de producción que eluda la inyección. Las fábricas
`restore` conservan fechas e ids almacenados y no consumen reloj/generador.

## Entregables

Puertos, adaptadores, cableado y migración de todos los consumidores actuales
en domain/application; fixtures, pruebas y mappers actualizados. Inventariar de
nuevo llamadas estáticas al implementar, porque otros frentes pueden agregarlas.
Documentar zona, comportamiento y configuración local.

## Criterios de aceptación

1. Con reloj fijo, registro de equipo, verificación documental, apertura/resolución
   de apelación, generación/publicación de ranking y auditoría guardan tiempos
   previstos sin depender del momento en que corre el test.
2. Con generador secuencial se conocen ids de rondas, slots, apelaciones, rankings
   y eventos. Ids aportados por cliente o derivados permanecen iguales.
3. Un restore conserva tiempos e ids y no consume valores de los puertos.
4. No hay `now()` del sistema ni `UUID.randomUUID()` en producción de
   domain/application. Los adaptadores externos contienen esas decisiones.
5. Pruebas de JPA/API siguen leyendo historial y JSON con las formas existentes;
   rechazos y transacciones mantienen su comportamiento.

## Fuera de alcance

Versionar tablas, corregir estados de apelaciones de otro frente, introducir
event sourcing, cambiar formato de timestamps o eliminar VOs sin relación con
este cambio. No alterar la identidad de intentos ni recalcular puntajes históricos.

## Implementación y verificación

- Puertos en `com.roboleague.support`: Clock e IdGenerator. Adaptadores
  SystemClock/UuidGenerator en infrastructure y beans en UseCaseConfig.
- `ROBOLEAGUE_TIME_ZONE` configura la zona; default
  `America/Argentina/Buenos_Aires`. Se conservaron formatos, columnas e ids existentes.
- Agregados reciben fechas y OperationAudit; las fábricas de eventos reciben
  EventMetadata explícito. OperationAudit prepara dos ids, de los que una
  transición que agrega un solo evento usa únicamente el primero.
- TeamMember.of valida con fecha explícita; el constructor de valor permite
  rehidratación sin consultar el reloj. Mappers de intentos/apelaciones conservan
  tiempos e historial sin usar puertos.
- Fixtures y consumidores migrados. El flujo de competencia prueba secuencias
  locales de ids y avance controlado del reloj; API verifica timestamp de revisión
  con un reloj fijo; SystemClockTest comprueba cambio de fecha por zona.
- Verificación realizada: `mvn -B -o verify`, con Docker/Testcontainers:
  **399 tests**, cero fallos, errores o tests omitidos. También se verificaron
  referencias Markdown y `git diff --check`.
- La fecha de elegibilidad sigue siendo la del reloj suministrado al ensamblado
  actual; cambiarla a fecha de edición y sostener la elegibilidad corresponde a 03.
