# 05 — Presentación oral: guion, demo y preguntas

Estado: planificada. Fase 6. Es un documento para el **equipo**: presenta un
integrante elegido al azar al empezar la clase, así que los cinco tienen que
poder dar esta charla entera. Los números marcados `[medir]` se completan con
los valores de `docs/graphql-vs-rest.md` cuando la fase 5 termine.

Formato que pide la consigna: Dominio → Propuesta de valor → Decisiones clave
→ Preguntas. "La gestión del tiempo disponible es parte de la evaluación":
confirmar con la cátedra cuántos minutos hay y ajustar la tabla de abajo.
Supuesto de trabajo: 15 minutos de exposición + preguntas.

## Reparto del tiempo

| Bloque | Minutos | Qué se muestra |
| --- | --- | --- |
| 1. Dominio desde cero | 4 | Un diagrama de los siete agregados y el flujo "en seis pasos" |
| 2. Propuesta de valor: GraphQL | 6 | GraphiQL en vivo: consulta A, contraste con REST, error, N+1 |
| 3. Decisiones clave | 4 | Tabla D1–D12 con alternativa y consecuencia |
| 4. Preguntas | resto | Cualquier integrante responde |

## Bloque 1: el dominio (la audiencia no lo conoce)

Contar con una sola frase por concepto, en este orden, apoyándose en la demo:

1. **Problema**: una organización corre competencias de robótica con
   categorías, desafíos y rondas; cada desafío puntúa distinto; los
   resultados se recalculan con la versión exacta de reglas; todo puntaje se
   explica; nada se sobreescribe.
2. **Actores**: organizador (edición, categorías, desafíos, publica tabla),
   equipo (se inscribe, corre, apela), juez (asignado a un turno, carga
   mediciones o panel), árbitro de apelaciones.
3. **Agregados** (uno por frase): `Edition` (categorías e inscripciones),
   `Team` (estado canónico, inmutable), `Challenge` (reglamentos versionados,
   nunca se editan), `Round`/`Slot` (programación de turnos con pista y
   juez), `Attempt` (identidad `<slot>-<n>`, fuentes, revisiones append-only,
   estados), `Appeal` (máquina de estados), `Standings` (versiones, solo la
   última se publica).
4. **Reglas centrales**: el puntaje se calcula con la versión que el intento
   guardó al abrirse; la tabla recalcula **posiciones**, no re-evalúa pista;
   publicar exige sin apelaciones abiertas, sin turnos sin resultado y sin
   resultados posteriores.
5. **Puntos de extensión**: una regla nueva = una clase + registro en
   `RuleCatalog`; un criterio de desempate = una clase; una selección de
   rondas/intentos = una clase; un endpoint o un campo GraphQL = un adaptador
   que llama al mismo caso de uso.
6. **Arquitectura**: hexagonal en cuatro módulos; domain y application sin
   frameworks; Postgres con JSONB por agregado; cada caso de uso en una
   transacción; CI con unit, repositorio (Testcontainers) y API.

Historia de la demo para apoyarse: Laberinto, mejores 3 de 5, tres equipos,
TitanTeam apela la ronda 2 (`demo-7-1`), el recálculo da la v2 donde los tres
empatan en 717 y desempata por tiempo y después por descuento; la v2 es la
oficial. Rescate es el desafío mixto: `demo-19-1` espera el panel.

## Bloque 2: propuesta de valor (demo en vivo)

Preparación antes de la clase (en la máquina que se lleva):

```bash
docker compose up -d --wait
mvn -B package -DskipTests
java -jar roboleague-api/target/roboleague-api-1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

Abrir `http://localhost:8080/graphiql` y una terminal con los `curl` del
README listos en el historial. Tener `docs/graphql-vs-rest.md` abierto.

Guion:

1. **Qué es** (30 s): un esquema tipado de consulta; el cliente escribe qué
   campos quiere y recibe exactamente eso, anidado, en un viaje. Convive con
   REST: REST escribe, GraphQL consulta.
2. **Por qué acá** (30 s): los consumidores son distintos (pantalla de pista,
   app de equipo, prensa) y cada uno quiere otra combinación de los mismos
   datos. Con REST cada combinación es un endpoint nuevo o varios viajes.
3. **Consulta A en GraphiQL** (2 min): `team(id: "t-b") { … results(challengeId: "ch-maze") … breakdown … }`.
   Mostrar el autocompletado del esquema (eso es el contrato). Señalar en la
   respuesta: rondas en orden, `demo-7-1` con dos revisiones y 257,5, el ítem
   "Tope de bonificaciones" en `items`.
4. **Contraste REST** (1,5 min): la misma pantalla en REST son `[medir]`
   pedidos (`rounds` → `slots/{id}/results` × 4 → `breakdown` × 4) y
   `[medir]` bytes contra `[medir]`; mostrar un `GET /challenges/ch-maze/rounds?categoryId=cat-junior`
   y señalar todo lo que la pantalla no usa (slots de otros equipos, jueces,
   pistas). Eso es sobre-obtención; que no traiga el desglose es
   sub-obtención.
5. **Consulta B, podio** (30 s): `standings(...) { official { entries { position teamName total explanation } } }`;
   contra `GET .../standings` que trae `versions`, `latest`, `official`,
   `pending` y `considered`/`discarded` por fila. Agregar `team { institution }`
   en vivo: el campo cuesta una consulta más, **una**, no tres: pasar al punto 6.
6. **N+1** (1 min): abrir `GraphQlBatchingTest`: la diferencia de sentencias
   entre pedir `teamName` (gratis, viene en la versión) y `team { institution }`
   es 1 para tres equipos. Antes del `@BatchMapping` era 3 (`[medir]`, anotado
   en el doc). Explicar en una frase por qué existe: cada caso de uso es su
   propia transacción, no hay sesión compartida entre filas.
7. **Error** (30 s): `edition(id: "nope") { name }` → `data.edition: null` y
   `errors[0].extensions.classification: NOT_FOUND`; escribir un campo mal y
   mostrar que la validación lo rechaza antes de ejecutar.
8. **Qué no se expone y por qué** (30 s): mutaciones (D2), el reglamento
   completo, métricas de apelación. "Cuando dudás, dejalo afuera: se puede
   agregar, no se puede quitar" (clase 9).

## Bloque 3: decisiones clave

Llevar la tabla D1–D12 del README de estas specs en una slide, con tres
columnas: alternativas consideradas, decisión, consecuencia observada. Las
que más preguntas generan, con la respuesta corta:

- **¿Por qué no devolver los agregados directo al esquema?** Expondría
  `EditionId`/`Points` sin conversión y ataría el esquema a nombres internos;
  los views son el contrato, igual que los DTO en REST (D4).
- **¿Por qué no un módulo aparte?** No habría clase de arranque para
  testearlo sin depender de api; el costo es un paquete más dentro de api,
  separado por nombre (D1).
- **¿Duplican lógica?** No: `grep Repository` en el paquete graphql da vacío;
  cada campo llama al mismo caso de uso que REST. Lo que se agregó en
  application son dos lecturas (`QueryTeams`, `QueryTeamResults`) y un
  método, Java plano con tests propios.
- **¿Trataron el N+1 en todos lados?** En toda relación que se resuelve por
  id: `team`, `attempt`, `breakdown`, `round`, `challenge`, `edition`/`category`
  y `turn`, cada una con su test de conteo (tabla "Estrategia por relación"
  del README de las specs). Las listas por padre (`challenges` de una edición,
  `appeals` de un intento) cuestan una lectura por padre, nunca por hijo;
  su lote es deuda documentada (cap. 8).
- **¿Y si no hay internet en el aula?** GraphiQL carga de un CDN y queda en
  blanco; las mismas consultas están como `curl` en el README y se ensayaron
  sin red (plan B, checklist).
- **¿Por qué `Slot.turn` y no los campos sueltos?** Un campo = un lote por
  ronda; cuatro campos sueltos serían cuatro cargas de lo mismo (D12). Es el
  mismo agrupamiento que `GET /slots/{id}/results`.
- **¿Y la subscription?** No se hizo: exige WebSocket y publicar eventos de
  dominio, que el diseño descartó para el recálculo; el camino está escrito (D9).
- **¿Qué gana REST?** Caché HTTP por URL, códigos de estado por recurso,
  escrituras con resultados `sealed`. Por eso convive y no reemplaza.

## Bloque 4: preguntas probables

Respuestas de una o dos frases; cualquiera del equipo las tiene que poder dar.

| Pregunta | Respuesta |
| --- | --- |
| ¿Qué pasa si una consulta pide todo el grafo recursivamente? | Límite de profundidad 10 por instrumentación (D8); una consulta más profunda se rechaza antes de ejecutar. Sin límite de tamaño de lista: deuda registrada. |
| ¿Cómo se versiona el esquema? | Agregando campos; no se quitan ni cambian de tipo. Si hubiera que deprecar, `@deprecated` en el SDL. |
| ¿Dónde están las transacciones en GraphQL? | Igual que REST: cada caso de uso es una transacción (`TransactionalUseCases`); un request GraphQL con varios campos son varias transacciones de lectura. Como no escribe, no hace falta atomicidad entre campos. |
| ¿Cómo evitan que GraphQL rompa el dominio? | Dependencias Maven: domain y application no conocen `spring-graphql`; el esquema y los views están en api; la regla "resolver → caso de uso" se verifica con `grep`. |
| ¿Por qué 200 con `errors` y no 404? | Es el protocolo GraphQL: una consulta puede fallar en una rama y responder las otras; la clasificación va en `extensions`. |
| ¿Cómo probaron el N+1? | Estadísticas de Hibernate en el contexto de test: se cuentan sentencias preparadas con y sin el campo; para `team` la diferencia es 1 con el lote y N sin él; cada lote tiene su aserción con la constante explicada. |
| ¿Por qué 200 y `INTERNAL_ERROR` si la base está caída, y no 503 como REST? | `ErrorType` de Spring for GraphQL no tiene `SERVICE_UNAVAILABLE`; se devuelve `INTERNAL_ERROR` con el mismo texto que REST y se loguea igual (D6). |
| ¿Los puntos no eran exactos? | En el modelo sí (`Points`, DESIGN 2.28). GraphQL los muestra como `Float`, igual que REST con `Double`: es presentación, el cálculo no pasa por ahí (D4). |
| ¿Qué es DataLoader y qué hace `@BatchMapping`? | DataLoader junta las claves que los resolvers de un mismo nivel piden y las carga de una vez; `@BatchMapping` declara un resolver que recibe la lista de padres y devuelve el mapa padre → hijo, y Spring registra el DataLoader. |
| ¿Puede un cliente escribir por GraphQL? | No; las escrituras tienen resultados `sealed` con códigos por caso (201/409/422) y siguen en REST (D2). |
| ¿Cuánto costó? | Dos casos de uso de lectura, un método de puerto en dos adaptadores, un paquete en api con esquema, ~30 views, 6 clases de resolvers, 1 resolver de errores y 7 tests. Cero cambios en domain salvo el puerto. |
| ¿Qué harían distinto con más tiempo? | Lotes para las listas por padre, subscription del ranking, complejidad por costo en vez de profundidad, una pantalla del front sobre GraphQL, GraphiQL servido sin CDN. |

## Ensayo

- Cada integrante hace la demo completa una vez con la demo cargada, cronometrada.
- Además de la demo entera, cada uno prepara en profundidad la parte del
  "Reparto sugerido" del README de estas specs (esquema, application, lotes,
  errores/tests, docs): si le toca presentar, esa es la parte donde puede
  entrar al código en vivo si la cátedra lo pide; para las otras cuatro
  alcanza con el guion y la tabla de preguntas.
- Si GraphiQL no abre: usar los `curl` del README (mismas consultas).
- Si Docker falla en la máquina: tener grabado un GIF de la consulta A y B y
  el test de N+1 corriendo (`mvn -B -pl roboleague-api -am -Dtest=GraphQlBatchingTest -Dsurefire.failIfNoSpecifiedTests=false test`).
