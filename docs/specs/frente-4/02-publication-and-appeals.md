# 02 — Publicación y ciclo de apelaciones

Estado: implementado.

## Publicación

`PublishStandingsUseCase` pide publicar una versión. `Standings.publish` recibe
un `PublicationCheck` (tabla actual, apelaciones abiertas, turnos sin resultado)
y devuelve `Published` o `Blocked(reasons)`. No hay `if` por tipo de apelación:
el intento cuenta las abiertas. Un turno cancelado no exige resultado.

Solo la última versión se puede oficializar. Publicar reemplaza a la oficial
anterior; esa versión queda `REPLACED` y conserva su `OfficialPublication`.

## Apelaciones

`FileAppealUseCase` presenta el reclamo y marca el intento. `ReviewAppealUseCase`
lo toma. `ResolveAppealUseCase.acceptAppeal` exige el reporte corregido de al
menos una fuente, lo valida contra el reglamento del intento y, si cabe,
ajusta el intento y recalcula las standings. Si no cabe, `Invalid` y no escribe.
Rechazar conserva el puntaje; con dos apelaciones abiertas el intento sigue
`UNDER_APPEAL`.

`Appeal` no expone setters. `restore` es solo para persistencia.
