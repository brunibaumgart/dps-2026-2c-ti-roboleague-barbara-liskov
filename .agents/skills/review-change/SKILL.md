---
name: review-change
description: Revisar un diff de RoboLeague y detectar regresiones concretas en dominio, casos de uso, persistencia, transacciones y contratos HTTP. Usar cuando se solicita una revisión de cambios, PR o implementación.
---

# Revisar cambios en RoboLeague

Ubicar la raíz del repo y leer `AGENTS.md` más los archivos de los módulos
afectados. Confirmar el alcance de revisión: diff de trabajo, commit o rama base
indicados. Incluir archivos nuevos si forman parte del cambio. No modificar
la implementación durante una revisión salvo que también se haya pedido corregirla.

## Seguir el comportamiento

Leer consumidores y pruebas del código modificado, no solo líneas del diff.
Contrastar con los contratos de `README.md` y las secciones pertinentes de
`DESIGN.md`. Evaluar los puntos siguientes únicamente donde el cambio los afecte:

- Dependencias: domain/application siguen libres de frameworks; application
  usa infrastructure solo en tests; puertos permanecen en domain.
- Capturas: identidad por slot/número, equipo y juez válidos, fuente requerida,
  métricas declaradas y rechazo sin mutación. Puntaje solo al completar fuentes.
- Reglamentos/scoring: versionado inmutable, referencia del intento conservada,
  catálogo compartido, parámetros desconocidos rechazados, tope de bonos y piso
  en cero explicados por los ítems, ausencia de puntaje distinta de cero.
- Estados/auditoría: transiciones válidas, varias apelaciones abiertas, revisiones
  anexadas y rehidratación que conserva el historial sin recalcularlo.
- Rankings: alcance real por edición/categoría/ronda, desempates y selección de
  puntajes computables; apelaciones ajenas a la tabla no bloquean publicación.
  No asumir integración automática entre `evaluation/scheme` y todos los
  consumidores de `RankingCalculatorService`.
- Persistencia: nueva migración para esquema, ida y vuelta de JSONB, versiones
  históricas legibles, control optimista de intentos y conflictos concurrentes.
- Transacciones: casos de uso siguen interceptados por `TransactionalUseCases`,
  con paquete y métodos aptos para proxies; atomicidad de escrituras Postgres.
  Los repositorios en memoria no adquieren rollback por estar dentro del proxy.
- API: DTOs propios, códigos y `ErrorDto` coherentes, JSON desconocido rechazado,
  bean de repositorio único y tests con ids que no colisionen en contexto compartido.

## Sustentar y comunicar hallazgos

Usar pruebas existentes o una reproducción proporcionada cuando permitan
confirmar el problema. Para seleccionar verificaciones, consultar
`../verify-change/SKILL.md`. No afirmar que una suite pasó sin ejecutarla.

Reportar primero problemas que afecten corrección, datos o compatibilidad. Cada
hallazgo debe identificar archivo/línea, condición que lo dispara y consecuencia.
Distinguir errores introducidos por el cambio de limitaciones preexistentes;
evitar convertir preferencias de estilo en defectos. Si no hay hallazgos,
decirlo e indicar verificaciones realizadas y límites relevantes de cobertura.
