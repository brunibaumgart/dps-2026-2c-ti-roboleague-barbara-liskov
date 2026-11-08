# REST y GraphQL en RoboLeague: dos casos medidos

> **Borrador para completar con la demo levantada.** Las celdas marcadas
> `⟨medir⟩` se reemplazan por el número real antes de entregar (la spec 04 pide
> no dejar placeholders). Al final está la lista de qué medir y con qué
> comando.

**Cómo se midió:** demo cargada (`java -jar roboleague-api/target/*.jar
--spring.profiles.active=demo`), `curl -s -o /dev/null -w '%{size_download}'`
para los bytes de cada pedido REST y el mismo `curl` con el documento GraphQL
para GraphQL. Fecha: ⟨medir⟩. Commit: ⟨medir⟩.

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
| Peticiones | 1 + ⟨medir: turnos de t-b⟩ + ⟨medir: intentos⟩ = ⟨medir⟩ (la spec estima 1 + 4 + 4 = 9) | 1 |
| Bytes recibidos | ⟨medir: suma de todos⟩ | ⟨medir⟩ |
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
| Peticiones | 1 (`GET /challenges/ch-maze/standings?categoryId=cat-junior`) + 1 por fila para la institución (`GET /editions/ed-1/registrations/{teamId}`) = 1 + ⟨medir: filas⟩ | 1 |
| Bytes | ⟨medir⟩. Trae `versions`, `latest` y `official` completos, con `considered`/`discarded` de cada fila, y `pending`. | ⟨medir⟩ (5 campos por fila) |
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

**Consulta:** `standings { latest { entries { team { institution } } } }` con
⟨medir⟩ equipos.

| Implementación | Sentencias SQL para `team` | Dónde se mide |
| --- | --- | --- |
| `@SchemaMapping` por fila (`QueryTeamsUseCase.get`) | ⟨medir⟩ (una por fila) | Medición de @nnicokim antes del cambio |
| `@BatchMapping` + `QueryTeamsUseCase.byIds` | ⟨medir⟩ (una para todas) | Test de conteo de `team` |

Los demás lotes, medidos directamente en lote (un test de conteo por campo, que
afirma lo mismo con N = 1 y N = 3 filas):

| Relación | Sentencias | Test |
| --- | --- | --- |
| `RoundResult.attempt`, `Appeal.attempt` | ⟨medir⟩ | @cachoJDG |
| `RoundResult.round`, `Attempt.round` | ⟨medir⟩ | @cachoJDG |
| `Round.challenge` | ⟨medir⟩ | @cachoJDG |
| `Round.category` | ⟨medir⟩ | @cachoJDG |
| `Attempt.breakdown` | 0 extra si el padre ya lo trajo; ⟨medir⟩ si no | @cachoJDG |
| `Slot.turn` | ⟨medir⟩ por (desafío, categoría) | @cachoJDG |
| `Challenge.edition` | ⟨medir⟩ | @Iker-Galante |

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

## Para completar (checklist de medición)

Con la demo levantada y las ramas de GraphQL mergeadas:

```bash
# bytes de un pedido REST
curl -s -o /dev/null -w '%{size_download}\n' 'localhost:8080/challenges/ch-maze/rounds?categoryId=cat-junior'

# bytes de una consulta GraphQL
curl -s -o /dev/null -w '%{size_download}\n' localhost:8080/graphql \
  -H 'Content-Type: application/json' -d @consulta-a.json
```

- [ ] Caso A: contar los turnos de `t-b` en Laberinto y sus intentos (fija el número de pedidos REST).
- [ ] Caso A: bytes de cada pedido REST y de la consulta GraphQL.
- [ ] Caso B: cantidad de filas del podio oficial; bytes REST y GraphQL.
- [ ] N+1: números de @nnicokim (antes y después de `team`) y de cada test de conteo.
- [ ] Fecha y commit de la medición.
- [ ] Borrar el aviso de borrador de arriba.
