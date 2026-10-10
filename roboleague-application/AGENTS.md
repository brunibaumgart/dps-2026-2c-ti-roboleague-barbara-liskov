# Casos de uso

Leer también `../AGENTS.md`. Producción en
`src/main/java/com/roboleague/usecase`; depende únicamente del dominio.

## Coordinación

- Inyectar puertos y servicios por constructor. Los puertos `*Repository` viven
  en domain; no usar clases concretas JPA/memory desde producción.
- El caso de uso carga agregados, invoca comportamiento de dominio y guarda el
  resultado. Mantener scoring, elegibilidad y transiciones dentro del dominio.
- Distinguir requisitos del flujo (cargar un slot y comprobar asignación del
  juez) de invariantes propias del modelo. Las invariantes deben cumplirse también
  al invocar el dominio sin HTTP; no confiar en validaciones del controller para
  mantener válido un agregado. No incorporar aquí validaciones de formato JSON.
- Usar commands/records y resultados `sealed` existentes cuando correspondan
  (`Reception`, `Publication`). No introducir DTOs HTTP ni códigos de respuesta.
- Preservar la distinción actual entre `IllegalArgumentException` para datos
  inválidos/ids inexistentes, `IllegalStateException` para conflictos y rechazos
  detallados de negocio. La API realiza su traducción HTTP.

## Inscripción y actualización

- RegisterTeamUseCase recibe edición/categoría y un Team nuevo, o un TeamId
  canónico. Un TeamId existente no se sobrescribe enviando otro Team en un alta.
  El resultado es Registration; el reloj aporta registeredAt, nunca la fecha
  usada para calcular edad.
- UpdateTeamUseCase recibe TeamId y candidato completo inmutable. Validar todas
  las ediciones devueltas por EditionRepository.findByTeamId antes de guardar;
  reunir motivos por edición. Cambiar categoría usa
  ChangeRegistrationCategoryUseCase y conserva fecha/hora de la inscripción.
- UpdateRegistrationUseCase coordina el candidato de equipo y categoría juntos:
  sustituye la edición destino por su candidata al validar todas las inscripciones.
  Validar contra la categoría anterior rechazaría cambios conjuntos válidos.
  Comparte validateRegistrations con UpdateTeamUseCase; ninguna escritura sucede
  antes de validar. Esto no garantiza rollback técnico de dos escrituras memory.
- GetRulebookUseCase lee una versión publicada por ChallengeId/RulebookVersion,
  sin generar versiones ni cambiar historial; el front usa las métricas fijadas
  de los intentos pendientes aunque se publique otro reglamento.
- QueryEditions/QueryRegistrations/QueryRounds y ListEditionChallenges son entradas
  de lectura: validan padres/filtros, entregan datos actuales y no escriben.
  RegistrationView reúne relación y Team canónico sin incluir conceptos HTTP.
- Programación revalida equipos canónicos antes de generar ids/guardar rondas.
  CategoryResultsReader resuelve los TeamIds de las registrations contra TeamRepository.
  No conservar listas paralelas de Team dentro de Edition.
- Los rechazos de validación no escriben ni mutan referencias ya guardadas.
  Esto no acredita rollback ante un fallo técnico entre escrituras en memoria.

## Flujos que requieren cuidado

- `ScheduleRoundUseCase` carga desafío/edición, verifica scope único, ordena
  inscripciones por registeredAt/TeamId y entrega las rondas guardadas al servicio
  de dominio para evitar conflictos. No trasladar el algoritmo al caso de uso.
- `ReceiveResultUseCase` busca el slot, exige que el challenge recibido coincida
  con la ronda incluso en la primera captura, valida el juez y toma su equipo. La primera captura abre el intento con el reglamento vigente;
  las siguientes cargan la versión fijada en el intento. Guardar solo capturas
  aceptadas, conservando la identidad derivada de slot/número.
- `ResolveAppealUseCase` acepta correcciones con el reglamento del intento,
  actualiza intento y apelación y recalcula las standings de su desafío y
  categoría al aceptar. Al rechazar, restaura el estado del intento mediante
  su transición de dominio. Correcciones que no caben en el reglamento vuelven
  como `AppealAcceptance.Invalid` y no escriben.
- `PublishStandingsUseCase` publica solo la última versión. El agregado bloquea
  si hay apelaciones abiertas, turnos sin resultado o resultados posteriores al
  cálculo. Un bloqueo no guarda nada y devuelve cada motivo.
- `RecalculateStandingsUseCase` calcula por desafío y categoría con el
  `RankingScheme` vigente del desafío; cada cálculo agrega una versión y no
  edita las anteriores.

## Integración y pruebas

Los casos de uso se registran en `UseCaseConfig` del módulo API. Allí
`TransactionalUseCases` envuelve los beans del paquete exacto
`com.roboleague.usecase` con proxies de clase: cambiar paquete o declarar clases/
métodos finales puede impedir la interceptación. Mantener las transacciones
fuera de este módulo y comprobar el cableado si se altera esa estructura.
La transacción cubre Postgres; los adaptadores en memoria no ofrecen rollback.

Tests en `src/test/java/com/roboleague/usecase`, normalmente con repositorios
en memoria de infrastructure (dependencia de test). Cubrir resultados y efectos
observables sobre agregados; usar `AppealAndRecalculateIntegrationTest` como
referencia para coordinación. Ejecutar desde la raíz:
`mvn -B -pl roboleague-application -am test`.
Cambios en transacciones o wiring requieren además las pruebas del módulo API.
