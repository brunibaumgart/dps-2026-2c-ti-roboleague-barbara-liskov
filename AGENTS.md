# RoboLeague: instrucciones compartidas para agentes

## Alcance y lectura

Estas instrucciones aplican a todo el repositorio. Antes de editar un módulo,
leer también su `AGENTS.md`; las instrucciones locales complementan estas reglas.
Los `CLAUDE.md` importan estos mismos archivos para Claude Code: mantener las
reglas en los `AGENTS.md`, evitando duplicarlas en los archivos de entrada.

- Consultar `README.md` para ejecución, contratos HTTP y extensión del proyecto.
- Consultar las secciones pertinentes de `DESIGN.md` para decisiones, patrones
  y alternativas. No es necesario cargar el documento completo en cada tarea.
- Contrastar la documentación con código y pruebas. Si difieren, señalar la
  diferencia y resolverla dentro del alcance pedido; no asumir que una intención
  documentada ya está implementada.

## Mapa y límites de arquitectura

Proyecto Maven multimódulo, Java 25 y Spring Boot (versión en `pom.xml`).
Los paquetes siguen bajo `com.roboleague`, sin prefijos Java por módulo.

La arquitectura es hexagonal (puertos y adaptadores), con modelado DDD y
separación dominio/aplicación compatible con la regla de dependencias de Clean
Architecture. El flujo de ejecución puede llegar a Postgres; las dependencias
del código apuntan al núcleo: infrastructure implementa puertos definidos en
domain. Los métodos de los casos de uso son la entrada al núcleo; no crear una
interfaz por clase solamente para reproducir un diagrama arquitectónico.

`tournament`, `scheduling`, `evaluation` y `ranking` son áreas del modelo que
comparten tipos. Sus paquetes no establecen por sí solos bounded contexts
independientes ni microservicios. Justificar nuevos límites por lenguaje,
invariantes y necesidades del negocio, antes de separar módulos o servicios.

| Módulo                      | Responsabilidad                                                | Dependencias de producción          |
| --------------------------- | -------------------------------------------------------------- | ----------------------------------- |
| `roboleague-domain`         | Agregados, objetos de valor, servicios y puertos `*Repository` | Java, sin frameworks                |
| `roboleague-application`    | Casos de uso en `com.roboleague.usecase`                       | domain                              |
| `roboleague-infrastructure` | Adaptadores JPA/Postgres y repositorios en memoria             | domain y frameworks de persistencia |
| `roboleague-api`            | REST, DTOs, composition root, transacciones y demo             | application e infrastructure        |

Application usa infrastructure **solo en tests**, para los adaptadores en memoria.
Mantener las dependencias hacia el dominio; no introducir HTTP, Spring, JPA o
Jackson en domain/application.

Persistencia actual: `Attempt`, `Challenge` y `Appeal` tienen adaptadores JPA.
`Edition`, `Team`, `Round` y `Ranking` siguen en memoria, cableados por
`InMemoryRepositoryConfig`. No asumir persistencia completa ni rollback de los
repositorios en memoria. Flyway administra el esquema y Hibernate lo valida.

## Criterios SOLID

- **SRP:** separar traducción HTTP, coordinación de casos de uso, reglas de
  negocio y conversión de persistencia según sus razones para cambiar.
- **OCP:** extender reglas, criterios y especificaciones mediante los contratos
  y composición existentes. Registrar un tipo en el catálogo es válido; evitar
  agregar condicionales de fórmulas a controllers o a `Attempt`.
- **LSP:** preservar resultados, errores y efectos observables del contrato al
  agregar implementaciones. Documentar diferencias de durabilidad, concurrencia
  y rollback entre adaptadores; una interfaz común no garantiza equivalencia.
- **ISP:** definir puertos según necesidades de sus consumidores. Evitar
  interfaces generales que obliguen a implementar operaciones ajenas o lanzar
  `UnsupportedOperationException` para métodos que el contrato promete soportar.
- **DIP:** el núcleo define los contratos que necesita y recibe adaptadores por
  constructor. La inyección de Spring no reemplaza esta regla de dependencias.

Agregar abstracciones cuando representen una variación o límite concreto;
no exigir una interfaz para cada clase ni patrones sin una necesidad del cambio.

## Convenciones de trabajo

- Trabajar sobre el alcance solicitado y respetar cambios existentes del equipo.
- Mantener nombres de tipos y métodos en inglés y el estilo Java del archivo.
  Documentar las instrucciones compartidas en español.
- Ubicar invariantes y cálculos en el dominio; coordinar repositorios desde los
  casos de uso y traducir entrada/salida en los adaptadores.
- Seguir las fábricas, records, resultados `sealed` y estados existentes cuando
  correspondan al problema. No agregar setters o anotaciones de framework a
  agregados para facilitar serialización o persistencia.
- Actualizar README si cambia un contrato o una forma de ejecutar/extender el
  proyecto; actualizar DESIGN si cambia una decisión de arquitectura.
- Crear specs o planes persistentes solo si la tarea concreta los necesita.
  No inventar requisitos ni agregar specs para mantenimiento rutinario.
- Al cerrar una tarea, informar cambios, verificaciones ejecutadas y límites
  pendientes. Distinguir pruebas omitidas de pruebas aprobadas.

## Ejecución y verificación

Ejecutar Maven desde la raíz. Requisitos: JDK 25, Maven 3.6.3+ y Docker para
pruebas de repositorio/API. La CI en `.github/workflows/ci.yml` usa `mvn -B verify`.

- `mvn -B -pl roboleague-domain test`: pruebas de dominio sin Docker.
- `mvn -B -pl roboleague-application -am test`: casos de uso y dependencias.
- `mvn -B verify`: verificación completa, incluidas pruebas con Testcontainers.
- `mvn -B package -DskipTests`: genera artefactos; no acredita pruebas.

Todos los tests se llaman `*Test`: `mvn test` también ejecuta los de repositorio
y API. Testcontainers levanta su propio Postgres; no requiere iniciar Compose.
El Postgres de `docker-compose.yml` se usa para ejecutar la aplicación local.
El perfil `demo` ejecuta `flyway.clean()` al iniciar y carga `DemoFixture`:
usarlo únicamente con una base destinada a la demo, nunca como prueba rutinaria
sobre una base cuyos datos deban conservarse.

## Procedimientos reutilizables

Leer el procedimiento correspondiente cuando la tarea lo requiera. Las rutas
son relativas a la raíz del repositorio y también pueden leerse desde Claude:

- `.agents/skills/verify-change/SKILL.md`: seleccionar verificaciones según los
  módulos afectados y registrar resultados.
- `.agents/skills/review-change/SKILL.md`: revisar un diff y reportar problemas
  concretos de comportamiento, integración o arquitectura.
