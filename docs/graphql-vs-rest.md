# REST y GraphQL en RoboLeague: dos casos medidos

**Cómo se midió:** demo cargada (`java -jar roboleague-api/target/*.jar
--spring.profiles.active=demo`), `curl -s -o /dev/null -w '%{size_download}'`
para los bytes de cada pedido REST y el mismo `curl` con el documento GraphQL
para GraphQL. Fecha: 2026-11-09. Commit: `2410351` (main) con la rama del
PR #32 (`044fec1`), que aporta `Query.team` y los lotes de `team`.

Los dos casos son clientes reales de una plataforma de competencias: una
pantalla en la pista y un medio de prensa. Cada uno necesita una combinación
distinta de los mismos datos, que es el problema que la consigna le asigna a
GraphQL.

---

## Caso A: pantalla de resultados en vivo de un equipo

**Cliente:** la pantalla de la pista muestra, para TitanTeam (`t-b`) en
Laberinto (`ch-maze`, categoría `cat-junior`), cada ronda con el puntaje del
intento y su desglose explicado.

**Qué necesita:** el nombre del equipo; por ronda, número y nombre; por
intento, id, estado, puntaje, ítems del desglose (concepto, métrica, fórmula,
subtotal), aporte por fuente y revisiones.

**GraphQL (1 pedido):**

```graphql
{
  team(id: "t-b") {
    name
    results(challengeId: "ch-maze") {
      round { number name }
      attempts {
        id status score
        breakdown {
          items { concept rawMetric formula subtotal }
          bySource { label subtotal }
          revisions { number reason total }
        }
      }
    }
  }
}
```

**REST:** no hay un recurso "resultados de un equipo". El cliente arma la
respuesta en tres niveles, y cada nivel depende del anterior:

1. `GET /challenges/ch-maze/rounds?categoryId=cat-junior` → las rondas, con
   **todos** los slots de todos los equipos. El cliente busca cuáles son de
   `t-b`.
2. `GET /slots/{slotId}/results`, uno por turno de `t-b` → los intentos de
   ese turno.
3. `GET /attempts/{id}/breakdown`, uno por intento → el desglose.

| | REST | GraphQL |
| --- | --- | --- |
| Peticiones | 1 + 4 turnos de `t-b` + 4 intentos = **9** | **1** |
| Bytes recibidos | **17 210** (rondas 4 610; cuatro `results` de 307 a 312; cuatro `breakdown` de 2 778 a 3 003) | **5 707** |
| Datos de más (sobre-obtención) | Las rondas traen los slots de los otros equipos, con pista, jueces y horarios. `results` trae `rulebookVersion`, `attemptRule` y `acceptsResult`, que la pantalla no muestra. `breakdown` trae `awaiting` y `notes`. | Ninguno: solo los campos pedidos. |
| Datos de menos (sub-obtención) | La ronda no trae los intentos ni el desglose: cada uno es otro viaje, y el cliente tiene que saber qué slot es de `t-b`. | Ninguno. |
| Viajes dependientes | 3 niveles (rondas → turnos → intentos) | 0 |

---

## Caso B: widget de podio para prensa

**Cliente:** una página externa muestra el podio oficial de Laberinto:
posición, nombre del equipo, total y explicación del puesto. Quiere sumar la
institución de cada equipo.

**GraphQL (1 pedido):**

```graphql
{
  standings(challengeId: "ch-maze", categoryId: "cat-junior") {
    official {
      number
      entries { position teamName total explanation team { institution } }
    }
  }
}
```

**REST:**

| | REST | GraphQL |
| --- | --- | --- |
| Peticiones | 1 (`GET /challenges/ch-maze/standings?categoryId=cat-junior`) + 1 por fila para la institución (`GET /editions/ed-1/registrations/{teamId}`) = 1 + 3 = **4** | **1** |
| Bytes | **6 011** (tabla 4 037 + tres inscripciones de 656 a 660). Trae `versions`, `latest` y `official` completos, con `considered`/`discarded` de cada fila, y `pending`. | **513** (5 campos por fila; 423 sin `team { institution }`) |
| Sobre-obtención | Todo lo anterior. Si la última versión es la oficial, `latest` y `official` llegan repetidas. | Ninguna. |
| Sub-obtención | La fila trae `teamId` y `teamName`, pero no la institución: un pedido más por fila. | `team { institution }` en la misma consulta, resuelto por lote. |

---

## Qué gana REST

- **Caché por URL**, gratis, en el navegador y en proxies HTTP. Una consulta
  GraphQL es un `POST` con un cuerpo distinto cada vez.
- **Códigos de estado por recurso.** GraphQL responde 200 y los errores van en
  `errors[]`, por campo.
- **Cuerpos fijos**, fáciles de documentar y validar.
- **Las escrituras siguen en REST** (D2): los resultados `sealed` de la
  captura y las apelaciones se traducen a 201, 409 y 422. GraphQL es solo de
  consulta.

Las dos APIs llaman a los mismos casos de uso. Ninguna regla de negocio está
duplicada.

---

## Evidencia del N+1

**Consulta:** `standings { latest { entries { team { id institution } } } }`
con una tabla de 1 fila y otra de 3 filas, misma consulta con y sin `team`.
Estadísticas de Hibernate sobre Postgres (Testcontainers), con warm-up y
`statistics.clear()` justo antes del pedido.

| Implementación | Filas | Sin `team` | Con `team` | Costo de `team` |
| --- | ---: | ---: | ---: | ---: |
| `@SchemaMapping` por fila (`QueryTeams.get`) | 1 | 8 | 9 | 1 |
| `@SchemaMapping` por fila (`QueryTeams.get`) | 3 | 8 | 11 | **3** (una por fila) |
| `@BatchMapping` + `QueryTeams.byIds` | 1 | 8 | 9 | 1 |
| `@BatchMapping` + `QueryTeams.byIds` | 3 | 8 | 9 | **1** (una para todas) |

La medición por fila es de la rama de @nnicokim antes de reemplazar el
resolver; con esa implementación el test de lote falla (delta 3 en vez de 1).
La medición en lote es `GraphQlTeamBatchingTest`, que también cubre los otros
cuatro campos `team` (`Registration`, `Slot`, `Attempt`, `Appeal`): 1 sentencia
extra con 1 y con 3 filas, 0 con el lote vacío.

Los demás lotes, medidos directamente en lote (un test de conteo por campo, que
afirma lo mismo con N = 1 y N = 3 filas):

| Relación | Sentencias extra (N = 1 y N = 3) | Test |
| --- | --- | --- |
| `RoundResult.attempt`, `Appeal.attempt` | 2 (intentos y sus desafíos, para explicar el puntaje) | `GraphQlEvaluationBatchingTest` |
| `RoundResult.round`, `Attempt.round` | 1 | `GraphQlEvaluationBatchingTest` |
| `Round.challenge` | 1 | `GraphQlEvaluationBatchingTest` |
| `Round.category` | 1 (las ediciones; la categoría se filtra de ellas) | `GraphQlEvaluationBatchingTest` |
| `Attempt.breakdown` | 0 si el padre ya lo trajo; 2 bajo un turno (intentos y desafíos) | `GraphQlEvaluationBatchingTest` |
| `Slot.turn` | 4 por (desafío, categoría): rondas, desafíos, rondas de la categoría, intentos de la categoría | `GraphQlEvaluationBatchingTest` |
| `Challenge.edition` | 1 (2 con raíces con alias: una para la primera y una para el resto) | `GraphQlRankingTest` |
| `*.team` (cinco campos) | 1 | `GraphQlTeamBatchingTest` |

**Por qué existe el N+1 acá:** cada caso de uso corre en su propia transacción
(`TransactionalUseCases`), así que no hay una sesión de Hibernate compartida
entre filas. GraphQL resuelve cada relación fila por fila salvo que se declare
el lote. `Team.results` no lo sufre porque `QueryTeamResultsUseCase` carga
rondas e intentos del equipo en 4 o 5 lecturas fijas. Las listas por padre
(`Edition.challenges`, `Attempt.appeals`…) cuestan una lectura por padre; su
lote queda como deuda (DESIGN, cap. 8).

---

## Decisiones del esquema (resumen; detalle en DESIGN 2.33)

| Qué | Decisión | Por qué |
| --- | --- | --- |
| Solo consultas | Sin `Mutation` (D2) | La consigna pide exponer el dominio de consulta; duplicar escrituras duplicaría sus contratos de error |
| Reglamento | Se expone versión, fuentes requeridas y reglas en palabras (`attemptRule`, `selectionRule`, `tieBreakCriteria`); no las reglas de puntaje | Es lo que necesita quien lee resultados; el reglamento completo sigue en `GET /challenges/{id}/rulebook/versions/{v}` |
| Métricas revisadas de una apelación | No se exponen | Son entrada de una escritura; el resultado se ve en `breakdown.revisions` |
| Búsquedas por id | Nullable: id inexistente → `null` + error `NOT_FOUND` (D6) | Un campo `!` arrastraría a `null` toda la rama |
| Fechas y puntos | `String` ISO y `Float`, igual que el JSON de REST (D4, D7) | Mismo valor en las dos APIs, sin scalars propios |
| Profundidad | Máximo 10, sin contar la introspección (D8) | El esquema tiene ciclos (`Challenge → edition → challenges`) |

---

## Cómo repetir la medición

Con la demo levantada:

```bash
# bytes de un pedido REST
curl -s -o /dev/null -w '%{size_download}\n' 'localhost:8080/challenges/ch-maze/rounds?categoryId=cat-junior'

# bytes de una consulta GraphQL (el documento de la consulta A en consulta-a.json)
curl -s -o /dev/null -w '%{size_download}\n' localhost:8080/graphql \
  -H 'Content-Type: application/json' -d @consulta-a.json
```

Los turnos de `t-b` en Laberinto son los slots `demo-3`, `demo-7`, `demo-11`
y `demo-15`; sus intentos, `demo-3-1`, `demo-7-1`, `demo-11-1` y `demo-15-1`.
Las sentencias SQL salen de los tests de conteo:
`mvn -B -pl roboleague-api -am -Dtest='GraphQl*BatchingTest,GraphQlRankingTest' -Dsurefire.failIfNoSpecifiedTests=false test`.
