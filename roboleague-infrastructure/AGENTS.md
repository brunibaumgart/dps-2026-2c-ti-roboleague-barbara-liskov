# Adaptadores de persistencia

Leer también `../AGENTS.md`. Adaptadores en
`src/main/java/com/roboleague/repository/{jpa,memory}`; puertos en domain.
No depender de application o API.

Los adaptadores de soporte `SystemClock` y `UuidGenerator` viven en
`com.roboleague.support`. El primero requiere zona explícita; ambos implementan
puertos de domain y se registran en la configuración API, sin acoplar el núcleo
a sus implementaciones.

## Estado actual y convenciones

- Postgres persiste `Appeal`, `Challenge` y `Attempt`; hay adaptadores en memoria
  para los siete puertos. La API selecciona cuáles usar.
- Respetar el contrato observable de cada puerto: identidad, resultado de
  búsquedas, errores y efectos de guardar. Las consultas no deben efectuar
  transiciones de negocio ni escrituras inesperadas. Documentar diferencias de
  concurrencia, durabilidad y rollback frente al adaptador en memoria; no exigir
  equivalencia transaccional que este no implementa. Cubrir los comportamientos
  compartidos del contrato y las garantías particulares del adaptador.
- Mantener entidad JPA propia, interfaz `SpringDataXxx`, mapper en ambos sentidos
  y `JpaXxxRepository implements XxxRepository`. Entidades y detalles de JSON
  quedan dentro del adaptador; no anotar agregados con JPA/Jackson.
- Rehidratar con `restore(...)`. Conservar estado, ids, tiempos, métricas,
  entregas, revisiones y eventos originales sin recrear acciones de negocio.
- `ChallengeMapper` guarda definiciones y reconstruye con `RuleCatalog`.
  Un reglamento persistido inválido es un error; no omitirlo ni sustituirlo por
  un reglamento por defecto. Preservar todas sus versiones.
- Los JSONB de historial y reglamentos son contratos persistidos. Al cambiar
  su forma o discriminadores, considerar lectura de datos existentes y migración.
- `JpaAttemptRepository` conserva la versión leída fuera del agregado y usa
  `saveAndFlush` con control optimista. Preservar rechazo de escrituras obsoletas
  y creaciones concurrentes del mismo intento; no convertirlas en sobrescrituras.

## Team, Edition y Registration (memoria)

TeamRepository es canónico y ya no consulta categoría: pertenece a Registration.
EditionRepository.findByTeamId busca ediciones con esa relación; guardar una
nueva versión inmutable reemplaza el snapshot del mismo id. Un getter no permite
modificar el objeto guardado. Esto evita aliases y cambios parciales por
validación, sin agregar garantías transaccionales ni de concurrencia.

Para los futuros mappers de frente 5: Edition.restore recibe context, fechas,
categorías e inscripciones completas; conservar TeamId/EditionId/CategoryId,
referenceDate y registeredAt. La clave de inscripción es edición/equipo. Team
se reconstruye con profile, robot, members y documentation; no lleva categoría
ni fecha de inscripción. Documentation.restore conserva documentos y metadatos
sin repetir verify. No replay de inscripción ni consulta del reloj al leer.

## Esquema y selección de adaptadores

Flyway usa `src/main/resources/db/migration/V<n>__<descripcion>.sql`.
Agregar una nueva migración para cambios de esquema; no editar migraciones
ya integradas. Hibernate usa `ddl-auto=validate`; no reemplazarlo por generación
automática para ocultar desajustes.

Al agregar persistencia para otro agregado, registrar su adaptador y retirar
el bean correspondiente de `InMemoryRepositoryConfig` en API. Evitar dos beans
para el mismo puerto. Revisar también el comportamiento de su adaptador en
memoria, utilizado por los tests de casos de uso.

## Verificación

Tests JPA en `src/test/java/com/roboleague/repository/jpa`: `@DataJpaTest`,
Postgres real con Testcontainers, `PostgresContainer` y configuración de
`InfrastructureTestApplication`. Seguir las importaciones/configuración de un
test vecino para la versión de Spring Boot usada aquí.

Verificar ida y vuelta completa, búsquedas y conservación del historial, no
solo que `save` termina. Para cambios en intentos, revisar las pruebas de
concurrencia de `JpaAttemptRepositoryTest`. Ejecutar desde la raíz con Docker:
`mvn -B -pl roboleague-infrastructure -am test`.
Cambios de wiring o atomicidad requieren además pruebas API; Compose no es
necesario para Testcontainers.
