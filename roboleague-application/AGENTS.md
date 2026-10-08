# Casos de uso

Leer también `../AGENTS.md`. Producción en
`src/main/java/com/roboleague/usecase`; depende únicamente del dominio.

## Coordinación

- Inyectar puertos y servicios por constructor. Los puertos `*Repository` viven
  en domain; no usar clases concretas JPA/memory desde producción.
- El caso de uso carga agregados, invoca comportamiento de dominio y guarda el
  resultado. Mantener scoring, elegibilidad y transiciones dentro del dominio.
- Usar commands/records y resultados `sealed` existentes cuando correspondan
  (`Reception`, `Publication`). No introducir DTOs HTTP ni códigos de respuesta.
- Preservar la distinción actual entre `IllegalArgumentException` para datos
  inválidos/ids inexistentes, `IllegalStateException` para conflictos y rechazos
  detallados de negocio. La API realiza su traducción HTTP.

## Flujos que requieren cuidado

- `ReceiveResultUseCase` busca el slot, valida el juez asignado y toma el equipo
  del slot. La primera captura abre el intento con el reglamento vigente;
  las siguientes cargan la versión fijada en el intento. Guardar solo capturas
  aceptadas, conservando la identidad derivada de slot/número.
- `ResolveAppealUseCase` acepta correcciones con el reglamento del intento,
  actualiza intento y apelación y recalcula ranking al aceptar. Al rechazar,
  restaura el estado del intento mediante su transición de dominio.
- `PublishOfficialRankingUseCase` bloquea publicación por apelaciones abiertas
  que afectan la tabla; las de otros equipos/rondas no deben bloquearla por
  accidente. Consultar sus pruebas al cambiar el alcance.
- `RecalculateRankingUseCase` calcula por edición/categoría y filtro opcional
  de ronda; no asumir que su implementación agrega por desafío o utiliza todas
  las estrategias configurables de un `Rulebook`.

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
