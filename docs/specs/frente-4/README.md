# Frente 4: tabla, publicación y apelaciones

Estado: implementado. Contratos HTTP, dominio, casos de uso, persistencia de
standings y pantallas del front están cableados. La comprobación visual en
navegador sigue pendiente (mismo límite que la spec 06).

Fuente: Hoja de ruta RoboLeague, pasos 5–6 y hallazgos #5, #8 y #3. Se contrastó
con el código al implementar. El HTML no se versiona en este repo.

## Resultado

| Pieza | Dónde |
| --- | --- |
| Standings versionadas por desafío y categoría | `com.roboleague.ranking.Standings` |
| Publicación solo de la última versión | `Standings.publish` + `PublicationCheck` |
| Desempate del reglamento | `StandingsTable` usa `RankingScheme` |
| Apelación sin setters públicos | `Appeal` + `AppealState` |
| Casos de uso | File/Review/ResolveAppeal, Recalculate/Publish/QueryStandings, QueryAppeals |
| API | `AppealController`, `StandingsController` |
| Persistencia | `JpaStandingsRepository`, migración `V5` |
| Front | `appeals.js`, `standings.js` |

## Criterios observables

1. Recalcular agrega una versión y no edita las anteriores.
2. Publicar una versión que no es la última, o con apelaciones abiertas, turnos
   sin resultado o resultados posteriores, no guarda y dice cada motivo.
3. Publicar la última, cuando nada lo bloquea, la deja oficial y reemplaza a la
   anterior.
4. El orden y la explicación de cada puesto salen de la cadena del reglamento.
5. Presentar / revisar / aceptar / rechazar una apelación son transiciones del
   agregado. Aceptar puntúa con el reglamento del intento y recalcula la tabla.
6. El front no calcula posiciones ni decide si se puede publicar.

## Verificación

`mvn -B -pl roboleague-domain,roboleague-application,roboleague-api -am test`
y los tests de módulos JS. Docker hace falta para API e infrastructure.
