# 02 — Identidades tipadas en el núcleo

Estado: pendiente. Depende de 01. Cambia contratos compartidos; integrar como
una migración coherente, sin dejar el reactor entre dos modelos.

## Problema y alcance

`AttemptId`, `ChallengeId` y `RulebookVersion` ya son tipos del dominio. Team,
Judge, Slot, Round y varias referencias siguen usando String; un id de juez
puede pasarse donde se esperaba uno de equipo y compilar.

Agregar tipos de identidad para Team, Judge, Slot, Round, Edition, Category,
Appeal y Ranking, además de Participant/Robot/Track donde se intercambian sus
identidades. Reutilizar los tipos existentes. No agregar un tipo diferente para
cada etiqueta de texto (nombres, motivos o descripciones no son identidades).
Si el frente 4 ya agregó `StandingsId`, utilizar su contrato para ese modelo,
sin crear una segunda identidad equivalente.

## Contratos propuestos

- Records/VOs distintos con valor textual no nulo ni blanco, igualdad por valor
  dentro del mismo tipo y acceso explícito al texto en los bordes.
- No imponer UUID ni normalizar mayúsculas, espacios internos o ids antiguos.
- Firmas de entidades, commands de application, referencias y repositorios
  expresan los tipos correspondientes. El generador de 01 entrega texto que se
  convierte al tipo correcto en el punto de creación.
- `AttemptId` contiene `SlotId` más número positivo. Su representación externa
  permanece `<slot>-<numero>` y se parsea desde el último guion.
- Tipar referencias de autor/juez cuando el rol sea inequívoco. Cuando un autor
  puede tener roles distintos, usar una identidad de actor común explícita y
  conversiones conscientes; no asumir que todo autor de auditoría es JudgeId.

JSON/path variables y columnas siguen representando ids como strings. Controllers
y mappers hacen conversiones explícitas; ninguna entidad JPA ni DTO de transporte
se convierte en requisito del núcleo. No depender de serialización automática
de records para preservar contratos HTTP.

## Consumidores que revisar

Profiles/identities/scopes del dominio, mapas por id, puertos, adaptadores memory
y JPA, comandos y casos de uso, RulebookReference, entregas y auditoría, DTOs,
configuración, demo y pruebas de todos los módulos. Reemplazar conversiones a
String internas que anulen la protección del tipo; permitir texto en mappers,
JSON, parsing y generación de ids derivados.

## Criterios de aceptación

1. `TeamId` no es aceptado por una firma que requiere `JudgeId` o `SlotId`;
   sus consumidores compilan con tipos explícitos.
2. Null/blanco se rechazan y valores conocidos de fixtures son aceptados.
3. AttemptId parsea slots con guiones y conserva exactamente la forma textual.
4. Requests/responses existentes conservan strings para ids, no `{value: ...}`.
5. Lectura/escritura JPA conserva ids históricos y búsquedas; no se cambian
   claves primarias por adoptar records en Java.
6. Reactor completo compila y pruebas de dominio, casos de uso, JPA/API pasan.

## Fuera de alcance

Renombrar todos los VOs, revisar todo el diseño de constructores, crear una
jerarquía universal de identidades o cambiar ids ya persistidos.
