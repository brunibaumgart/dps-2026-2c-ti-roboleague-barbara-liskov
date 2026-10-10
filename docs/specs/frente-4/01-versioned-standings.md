# 01 — Standings versionadas y desempate del reglamento

Estado: implementado.

## Comportamiento

Hay una tabla por desafío y categoría (`StandingsId`). La primera llamada a
`RecalculateStandingsUseCase` crea el agregado; cada una siguiente agrega una
`StandingsVersion` provisional. `restore` solo lo usan los adaptadores.

`CategoryResultsReader` junta el `RankingScheme` vigente, los equipos
inscriptos en la categoría y las rondas de ese desafío. Un equipo sin resultado
entra con total 0. Un equipo de otra categoría no aparece.

`StandingsTable.rank` ordena con el esquema y explica cada puesto: primer
puesto, debajo por un criterio, o empate en todos. Las rondas consideradas y
las descartadas viajan en `TeamRounds`.

## Límites

El recálculo no vuelve a correr `ScoreRule` sobre las métricas (DESIGN 2.9).
`Edition`, `Team` y `Round` siguen en memoria; las standings sí van a Postgres.
