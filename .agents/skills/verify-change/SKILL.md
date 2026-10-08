---
name: verify-change
description: Seleccionar y ejecutar verificaciones de cambios en RoboLeague según los módulos afectados, incluyendo Maven, Testcontainers y cableado transaccional. Usar al validar una implementación o investigar un fallo de build o tests.
---

# Verificar cambios en RoboLeague

Ubicar la raíz por su `pom.xml` multimódulo; todas las rutas y comandos siguientes
parten de allí. Leer `AGENTS.md` y las instrucciones de los módulos afectados.

## Seleccionar cobertura

Examinar el diff y sus consumidores antes de elegir pruebas. Verificar JDK 25
y Maven mediante `java -version` y `mvn -version`. Comprobar disponibilidad de
Docker si las pruebas seleccionadas usan Postgres/Testcontainers.

| Cambio | Comando desde la raíz | Docker |
| --- | --- | --- |
| Dominio aislado | `mvn -B -pl roboleague-domain test` | No |
| Casos de uso | `mvn -B -pl roboleague-application -am test` | Sí, por los tests de infrastructure incluidos con `-am` |
| Adaptadores/esquema | `mvn -B -pl roboleague-infrastructure -am test` | Sí |
| HTTP, configuración o integración | `mvn -B -pl roboleague-api -am test` | Sí |
| Cambio transversal o comprobación de CI | `mvn -B verify` | Sí |

`-am` incluye los módulos requeridos y sus tests; application depende de
infrastructure para tests. Todos los tests se llaman `*Test`, incluidos los de
integración: `mvn test` no implica una corrida exclusivamente unitaria.
Testcontainers inicia su base; no levantar Compose ni el perfil demo para estos
tests. `demo` limpia la base al iniciar.

## Ejecutar e interpretar

- Cubrir comportamiento modificado y regresiones pertinentes. Una definición
  de reglamento afecta potencialmente catálogo, mappers y HTTP; un cambio de
  cableado/transacciones requiere API además de pruebas en memoria.
- Para seleccionar una clase existente en el reactor, por ejemplo:
  `mvn -B -pl roboleague-api -am -Dtest=TransactionalUseCasesTest -Dsurefire.failIfNoSpecifiedTests=false test`.
  La opción permite módulos sin esa clase; confirmar en el resumen/reportes
  que el test solicitado sí se ejecutó. No confundir ausencia de tests con éxito.
- Para una selección de casos de uso sin Docker, usar nombres de tests de
  application con la misma opción de Surefire, comprobando que la selección
  no incluya también pruebas JPA de infrastructure.
- Ante un fallo, consultar el primer error relevante y los reportes
  `<modulo>/target/surefire-reports`. Distinguir compilación, aserciones y
  entorno (JDK, Docker, conexión o resolución de dependencias).
- Corregir dentro del alcance y repetir las verificaciones afectadas. No
  desactivar tests, sustituir Postgres por H2 ni alterar configuración para
  obtener un resultado verde sin resolver el problema.
- Cambios solo de Markdown requieren comprobar rutas, enlaces, imports y
  comandos contra el repo; no necesitan levantar la app ni ejecutar toda la suite.
- `package -DskipTests` comprueba empaquetado, no comportamiento. Si falta un
  requisito de entorno, informar qué comprobaciones pudieron ejecutarse y cuáles
  quedaron pendientes. Respetar los permisos de ejecución de la herramienta.

Reportar comandos ejecutados, resultado y cobertura pendiente. No atribuir a
una corrida local parcial la cobertura de `mvn -B verify` de CI.
