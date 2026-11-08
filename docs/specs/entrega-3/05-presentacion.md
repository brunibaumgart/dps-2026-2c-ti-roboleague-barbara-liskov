# 05 — Presentación en video: guion por integrante

Estado: guion listo para ensayar. Los números marcados `⟨medir⟩` salen de
`docs/graphql-vs-rest.md` cuando termine la medición.

## Qué pide la cátedra (consigna publicada para la Entrega 3)

La consigna publicada en el campus reemplaza al PDF en lo que difieren:

- **Video de YouTube no listado, de 8 a 12 minutos.** Sin video no se evalúa
  el TPI.
- **Se ven y hablan los 5 integrantes**, cada uno de al menos una parte
  distinta del sistema.
- **Contenido mínimo:** decisiones de dominio, implementación de los
  endpoints, refactors entre entregas y propuesta de valor. **En cada uno,
  qué alternativas se exploraron y por qué se eligió la que quedó.**
- La forma es libre: alcanza con una llamada de Meet grabada y bien
  presentada.

Se entregan dos links: el repo con el hash del último commit, y el video.

## Reparto del tiempo (objetivo: 10 a 11 minutos)

| # | Quién | Bloque | Tiempo | En pantalla |
| --- | --- | --- | --- | --- |
| 0 | @brunibaumgart | Apertura | 0:20 | Cámaras de los 5 |
| 1 | @brunibaumgart | Decisiones de dominio | 2:00 | Diagrama de agregados, DESIGN cap. 3 |
| 2 | @Iker-Galante | Implementación de los endpoints | 2:00 | README «Contratos», un `curl`, `ErrorDto` |
| 3 | @nnicokim | Refactors de la Entrega 1 a la 2 | 2:00 | Antes/después en código o DESIGN 2.8 y 2.26 |
| 4 | @cachoJDG | Refactors de la Entrega 2 a la 3 | 2:00 | DESIGN 2.32 y 2.34, `InputPortBoundaryTest` |
| 5 | @intilauberer | Propuesta de valor: GraphQL | 2:30 | GraphiQL o `curl` en vivo, `graphql-vs-rest.md` |
| 6 | @intilauberer | Cierre | 0:20 | Cámaras de los 5 |

**Regla para todos:** en cada decisión, decir las tres cosas en este orden:
**qué elegimos, qué alternativa descartamos y por qué.** Si falta el "por
qué", la decisión no cuenta.

---

## 0. Apertura (@brunibaumgart, 20 s)

> Somos barbara-liskov. RoboLeague es una plataforma para competencias de
> robótica: configura el evento, inscribe equipos, programa rondas, captura
> resultados, calcula puntajes explicables, publica posiciones y gestiona
> apelaciones. Les contamos cuatro cosas: el dominio, los endpoints, cómo
> cambió el diseño entre entregas y la API GraphQL de esta entrega.

## 1. Decisiones de dominio (@brunibaumgart, 2 min)

**Arranque (20 s):** el problema en una frase. Cada desafío puntúa distinto,
los resultados se recalculan con la versión exacta del reglamento, todo
puntaje se explica y nada se sobreescribe.

**Decisiones (1 min 40 s):**

| Decisión | Alternativa descartada | Por qué |
| --- | --- | --- |
| Reglas de puntaje como **Strategy + Composite**, registradas en `RuleCatalog` (2.2) | Un `switch` por tipo de regla en un servicio | Una regla nueva es una clase y un registro; ninguna regla existente se toca (OCP) |
| **Reglamento inmutable y versionado por desafío** (`Rulebook`, 2.8) | Una política global que se edita | Editar reescribiría resultados ya publicados; la consigna pide recalcular con la versión exacta |
| **Auditoría append-only**: cada ajuste agrega una revisión con autor y motivo (2.3) | Sobreescribir el puntaje | La consigna pide conservar originales y modificaciones |
| **State** para el intento y la apelación (2.5, 2.18) | Un enum con `if` en cada operación | Cada estado decide qué acepta y rechaza el resto con un error que lo nombra; no hay un `if` por estado repartido en cada operación |
| **Desglose explicable** (`ScoreBreakdown` con un ítem por contribución) | Devolver un número | Sin el desglose no hay apelación posible |

**Mostrar:** el diagrama de los agregados (`Edition`, `Team`, `Challenge`,
`Round`, `Attempt`, `Appeal`, `Standings`) y la matriz del capítulo 3.

## 2. Implementación de los endpoints (@Iker-Galante, 2 min)

**Arquitectura (30 s):** hexagonal en cuatro módulos. Domain y application sin
frameworks; infrastructure implementa los puertos; api es el composition root
con REST y GraphQL. Los controllers solo traducen y llaman a un puerto de
entrada.

**Decisiones (1 min 30 s):**

| Decisión | Alternativa descartada | Por qué |
| --- | --- | --- |
| Recursos en plural y **transiciones como sub-recursos** (`/appeals/{id}/acceptance`, `/standings/versions/{v}/publication`) | Verbos en la URL (`/acceptAppeal`) | Convención REST de la clase 9: sustantivos y un conjunto chico de operaciones |
| **DTO propios** (`XxxDto.from(...)`), nunca agregados serializados | Exponer el dominio con anotaciones de Jackson | El dominio no puede cambiar el contrato, y no lleva anotaciones de framework |
| **Taxonomía de errores**: 400, 404, 409 (conflicto), 422 (regla de negocio), 500 y 503, con `ErrorDto {error, details}` | Solo 200/400/500 ("three's company", clase 9) o `ProblemDetail` | 409 y 422 le dicen al cliente cosas distintas: reintentar con otro dato o corregir el pedido |
| **Persistencia con JPA y un JSONB por agregado**, con locking optimista | Tablas hijas con `@OneToMany` | Cada agregado se lee y se escribe entero; nadie consulta los hijos por separado |
| **Transacción por caso de uso** con un proxy en el composition root (`TransactionalUseCases`) | `@Transactional` en application | Application queda sin Spring |
| Tests de repositorio y de API **contra Postgres real** (Testcontainers) | Mocks de los repositorios | La consigna pide que los mocks no reemplacen las pruebas de persistencia ni de API |

**Mostrar:** la tabla «Contratos» del README y un `curl` a
`/challenges/ch-maze/standings?categoryId=cat-junior`.

## 3. Refactors de la Entrega 1 a la 2 (@nnicokim, 2 min)

La devolución de la Entrega 1 marcó seis puntos y la Entrega 2 trajo F1, F2
y F3. Elegir tres o cuatro:

| Antes (Entrega 1) | Después (Entrega 2) | Por qué cambió |
| --- | --- | --- |
| El reglamento "inmutable" se podía modificar (`addRule` público) | `CompositeScoreRule` guarda `List.copyOf` y no tiene `addRule` | Con una regla agregada, el mismo intento pasaba de 205 a 175 puntos sin cambiar la versión |
| Una edición = una prueba | `Challenge` como agregado con su reglamento versionado (2.8) | La demo pedía tres desafíos en un evento, con reglas por desafío (F1, F2, F3 viven en el reglamento) |
| `Ranking` + `TieBreakerChain` en código | `Standings` versionadas; la cadena de desempate se declara en el reglamento (2.4, 2.26) | Una corrección no reescribe una tabla ya vista; publicar solo la última evita publicar una tabla desactualizada |
| La inscripción se validaba una vez | `Registration` por edición y revalidación al cambiar el equipo (2.22) | Un equipo podía dejar de ser elegible después de inscribirse |

**Costo del cambio (lo midió la devolución):** F1 agregó 13 clases y modificó
3, sin tocar ninguna regla de puntaje; F2 agregó 4. F3 sí modificó código
existente (cada regla declara su fuente) y quedó documentado.

**Mostrar:** DESIGN 2.8 y 2.26, o el diff de `CompositeScoreRule`.

## 4. Refactors de la Entrega 2 a la 3 (@cachoJDG, 2 min)

La devolución de la Entrega 2 marcó cinco puntos. Todos se resolvieron:

| Punto de la devolución | Qué se eligió | Alternativa descartada |
| --- | --- | --- |
| El número de intento lo elegía quien captura | El dominio lo asigna por orden de llegada (`TurnAttempts`); la captura nombra el turno | Validar el número que manda el cliente: seguía pudiendo saltear |
| La tabla se armaba con la última versión del reglamento | Cada categoría juega con la versión que puntuó sus intentos (`CategoryRulebook`) | Re-evaluar con la versión vigente (contradice la consigna); versión por intento (dos equipos con reglas distintas) |
| Cuántos intentos por turno y cuál cuenta | `AttemptSelection` en el reglamento: `best-attempt` o `last-attempt`, con `perTurn` | Fijarlo en código |
| ¿El tope de bonificaciones es por intento o por desafío? | Por intento, con cuatro razones (DESIGN 2.32) | Por desafío: depende de qué rondas cuentan, que depende de los puntajes; sería circular |
| Faltan puertos de entrada | Una interfaz por caso de uso en el dominio; los controllers solo conocen la interfaz; `toTeam(clock)` pasó al caso de uso (2.34) | Mantener los casos de uso concretos (era nuestra decisión en la Entrega 2) |

**Para decir:** el último punto lo habíamos descartado a propósito. La
devolución nos mostró que dejaba a los adaptadores acoplados a la
implementación, y con dos adaptadores (REST y GraphQL) la interfaz pasó a ser
el contrato común. `InputPortBoundaryTest` impide volver atrás.

**Mostrar:** DESIGN 2.32 y 2.34, y el test de límites.

## 5. Propuesta de valor: GraphQL (@intilauberer, 2 min 30 s)

1. **Qué y por qué (30 s):** la pantalla de la pista, la app de un equipo y la
   prensa piden combinaciones distintas de los mismos datos. Con REST, cada
   combinación es un endpoint nuevo o varios viajes. GraphQL convive con REST:
   REST escribe, GraphQL consulta, y los dos llaman a los mismos casos de uso.
2. **Consulta de la consigna en vivo (45 s):** `team(id: "t-b") { results(…) { round … attempts { breakdown { items … revisions … } } } }`,
   en un solo pedido.
3. **Contraste con REST (45 s):** la misma pantalla en REST son ⟨medir⟩
   pedidos en tres niveles y ⟨medir⟩ bytes contra ⟨medir⟩. Mostrar un
   `GET /challenges/ch-maze/rounds` y señalar lo que sobra (slots de otros
   equipos, jueces, pistas: sobre-obtención) y lo que falta (el desglose:
   sub-obtención).
4. **N+1 (20 s):** pedir `team { institution }` en cada fila del podio costaba
   ⟨medir⟩ sentencias; con el lote cuesta ⟨medir⟩. Existe porque cada caso de
   uso es su propia transacción.
5. **Decisiones del esquema (10 s):** solo consultas (D2), schema-first (D3),
   views propios (D4), búsquedas nullables con error `NOT_FOUND` (D6),
   profundidad 10 (D8). No se expone: mutaciones, reglas de puntaje completas,
   métricas de apelación.

**Alternativas para nombrar:** GraphQL en lugar de REST (se pierde caché HTTP
y códigos por recurso), mutaciones por GraphQL (duplicarían contratos), lote
solo para `team` (el resto quedaba como N+1 latente), subscription del ranking
(no se hizo: exige eventos de dominio que el diseño descartó).

## 6. Cierre (@intilauberer, 20 s)

> La aplicación tiene dos adaptadores de entrada sobre los mismos casos de uso,
> el dominio sigue sin frameworks y cada decisión está en DESIGN.md con su
> alternativa. Gracias.

---

## Cómo grabar

- **Meet grabado** alcanza. Cada uno con cámara encendida mientras habla y
  pantalla compartida cuando muestra algo.
- Un ensayo completo con cronómetro antes de grabar. Si un bloque se pasa de
  2:30, cortar filas de la tabla, no apurar el "por qué".
- Demo con la app levantada en el perfil `demo` (`docker compose up -d`,
  `java -jar … --spring.profiles.active=demo`). Si GraphiQL no carga (CDN),
  usar los `curl` del README.
- Subir a YouTube como **no listado** y verificar que dure entre 8 y 12
  minutos y que se vea y escuche a los 5.

## Anexo: preguntas que pueden hacer en clase

| Pregunta | Respuesta corta |
| --- | --- |
| ¿Duplican lógica en GraphQL? | No: cada campo llama al mismo caso de uso que REST, y `grep Repository` en el paquete graphql da vacío. |
| ¿Por qué 200 con `errors` y no 404? | Es el protocolo: una consulta puede fallar en una rama y responder las otras; la clasificación va en `extensions`. |
| ¿Qué pasa con una consulta recursiva? | Se rechaza antes de ejecutar si pasa los 10 niveles (D8). |
| ¿Por qué interfaces con una sola implementación, si la regla #18 dice que no? | Lo que las justifica es el límite del núcleo (puerto de entrada), no la cantidad de implementaciones (regla #37, DESIGN 2.34). |
| ¿Los puntos no eran exactos? | En el modelo sí (`Points`, 2.28); GraphQL los muestra como `Float`, igual que REST. |
| ¿Qué harían con más tiempo? | Lotes para las listas por padre, la subscription del ranking, límite por complejidad y GraphiQL sin CDN. |
