# 06 — Front web mínimo e integración de los flujos

Estado: entrega parcial; flujos disponibles implementados y verificados por módulos/HTTP.
Pendientes: integración de frente 4 y comprobación visual/interactiva en navegador.
Depende de 05 y de los contratos API de los frentes 1, 2 y 4.
Puede comenzar la estructura con 05; no se considera completo mientras las
operaciones de los otros frentes no puedan ejecutarse contra la API real.

## Diseño propuesto

Front HTML/CSS/JavaScript servido desde `roboleague-api/src/main/resources/static`,
con `index.html`, estilos y módulos JS separados por responsabilidades. Elegido
para una demo con el mismo jar/puerto y sin build Node adicional, dado que hoy
no hay aplicación frontend. Es un adaptador de entrada: consume HTTP, presenta
datos y captura acciones; no calcula scoring, elegibilidad ni publicación.
Si el equipo ya integró un front equivalente, extenderlo en vez de crear otro.

Selección visible de edición, desafío y categoría, seguida de inscripción,
programación, resultados, desglose, apelaciones y tabla. Formularios utilizables
por personas, no una caja de JSON ni Swagger. IDs y valores conservan strings/
tipos del contrato; el usuario trabaja con nombres y el front guarda referencias.

## Funcionalidades y criterios observables

1. **Inscribir/editar equipo:** elegir categoría, completar miembros y robot,
   indicar documentación/verificador y mostrar restricciones. Errores 422 muestran
   todos los motivos; la UI solo refleja cambios confirmados por servidor.
2. **Programar:** indicar número, inicio, duración, pausa, pistas y jueces;
   presentar slots por pista y horario, con equipo/juez. Debe verse el paralelismo
   real de 04 y poder seleccionar un slot para capturar.
3. **Resultados:** indicar número positivo de intento y juez asignado, derivar
   identidad de slot/número y seleccionar fuente. Mostrar formularios separados
   para mediciones y panel, con métricas/unidades/rangos del reglamento, no un
   formulario fijo válido solo para Laberinto. Reutilizar GET challenge y PUT
   measurements/judge-scores existentes. El backend fija la versión histórica.
4. **Desglose:** mostrar fuente recibida/pendiente, versión del reglamento,
   ítems, fórmulas/subtotales, bonos/topes/deducciones y revisiones disponibles.
   Puntaje pendiente no aparece como cero definitivo. No recalcular el total local.
5. **Apelar:** presentar motivo/evidencia, revisar y aceptar/rechazar con métricas
   corregidas cuando corresponda. Mostrar errores de transiciones y el nuevo
   estado devuelto, sin editar historial existente.
6. **Tabla:** consultar tabla por desafío/categoría, versiones, puntajes y
   explicación de desempates; solicitar recálculo y publicar desde el estado
   del servidor. Mostrar por qué se rechaza publicación (versión vieja,
   apelaciones abiertas o capturas incompletas) cuando API lo informa.

No inventar control de roles como seguridad: pueden organizarse acciones de
organizador/juez/equipo sin afirmar autenticación o permisos que la API no tiene.

## Contratos externos pendientes

Al redactar, AppealController solo expone POST /appeals/{id}/review. Falta API
de presentación, aceptación/rechazo y consultas de apelaciones, y de tablas/
versiones/recálculo/publicación. Es responsabilidad del frente 4 exponerlos.
Antes de conectar esas pantallas, registrar en README rutas, bodies, DTOs,
estados y códigos reales acordados. Reusar sus nombres (`Ranking`/`Standings`)
sin crear un modelo de negocio paralelo en el navegador.

Si falta un endpoint, mostrar operación no disponible con causa concreta y
documentar pendiente; eso permite avance parcial, **no satisface** el criterio
final del flujo. No usar mocks en producción ni la lógica del simulador HTML
adjunto como sustituto de API. La hoja de ruta sirve de referencia funcional.

## Calidad de uso

Labels asociados, navegación por teclado, foco visible, mensajes legibles y
layout utilizable en escritorio. Estados de carga/vacío/error y botones
deshabilitados mientras un pedido está en curso para evitar doble envío accidental.
Un 409 obliga a recargar datos actuales antes de reintentar; no hacer retry
automático de mutaciones. Mostrar el ErrorDto sin perder details.
Renderizar nombres y motivos como texto (sin inyectarlos con innerHTML).
No enviar credenciales de BD ni exponer configuración interna.

## Verificación y demo

Usar la demo existente de tres desafíos como base, ajustándola a 03/04 sin
reescribir sus reglamentos. Documentar arranque con Compose/Maven/jar y URL
del front. Avisar en README que el perfil demo limpia su base; no agregar un
botón de borrado ni reiniciar automáticamente al abrir la página.

Comprobar en navegador contra backend real: inscripción válida e inelegible,
cambio rechazado sin pérdida, dos pistas en paralelo, captura mixta incompleta
y completa, desglose con tope, apelación que modifica resultado y recálculo/
publicación con historia conservada. La última parte requiere frente 4 listo.
Agregar verificación automatizada de flujos críticos cuando la herramienta del
repo lo permita y registrar por separado las comprobaciones manuales.

## Criterios de finalización

Las seis operaciones anteriores se realizan desde la web sin consola ni JSON
manual; errores reales se muestran correctamente; recargar consulta estado del
backend; la demo funciona con el arranque documentado y los contratos de API
tienen pruebas. Datos que todavía viven en memoria no se presentan como durables.
Documentar dependencias externas pendientes si se entrega solo una parte.

## Fuera de alcance

Copiar el simulador, generar reglas de negocio en JS, cambiar el scoring para
adaptarlo a la UI, implementar el frente 4 desde el frontend, login/roles reales,
hosting externo y diseño visual elaborado que bloquee el flujo funcional.

## Implementación realizada y límites de cierre

- index.html/styles.css y módulos JS nativos servidos por el mismo jar/puerto.
  Estética sobria y neutra, navegación de competencia, selección de contexto y
  layout escritorio; sin editor de JSON ni build npm.
- Inscribir/editar candidatos y elegir equipos existentes, restricciones,
  documentación/verificación; programación y turnos por pista; captura por
  fuente con métricas de reglamento; puntajes, aportes y revisiones disponibles.
- Consulta GET /challenges/{id}/rulebook/versions/{version} y GetRulebookUseCase
  agregado para mostrar el reglamento histórico fijado al abrir un intento.
- Servidor como fuente de verdad. IDs conservados al editar/reusar recursos,
  envíos bloqueados, 422 sin descartar formulario y 409 sin nuevos envíos hasta
  recargar. Texto seguro, sin innerHTML ni scoring/elegibilidad local.
- Apelaciones/tabla muestran operación no disponible por falta de sus APIs.
  POST review existe, pero no hay listado/consulta para seleccionar apelaciones.
  No se implementó frente 4 ni se simulan respuestas de producción.
- Verificación automatizada: Maven con Postgres/Testcontainers para assets,
  histórico y regresiones; Node para contratos/módulos HTTP, sin dependencias.
  Pruebas visuales e interactivas no ejecutadas: navegador de la sesión no
  disponible. README distingue cobertura automatizada de comprobaciones pendientes.
- **No se satisface todavía el cierre de las seis operaciones**: faltan APIs
  externas e interacción en navegador. Mantener este estado hasta verificarlas.
