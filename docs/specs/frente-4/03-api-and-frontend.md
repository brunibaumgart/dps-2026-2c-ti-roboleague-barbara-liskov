# 03 — API y front

Estado: implementado. Comprobación visual en navegador pendiente.

## Contratos

Los bodies y códigos están en el README. Resumen:

| Pedido | Éxito | Falla esperable |
| --- | --- | --- |
| POST /attempts/{id}/appeals | 201 | 400 equipo ajeno o intento inexistente |
| POST /appeals/{id}/review | 200 | 409 si ya está en revisión |
| POST /appeals/{id}/acceptance | 200 `{appeal, standingsVersion}` | 422 correcciones; 409 revisor o estado |
| POST /appeals/{id}/rejection | 200 | 409 revisor o estado |
| GET standings | 200, `latest`/`official` null si no hubo cálculo | 400 desafío/categoría |
| POST standings/versions | 201 | 400 |
| POST .../publication | 200 | 409 con cada motivo |

## Front

`appeals.js` y `standings.js` llaman esos endpoints. No hay mocks ni recálculo
local. Un 409 pide recargar, igual que el resto de la web.
