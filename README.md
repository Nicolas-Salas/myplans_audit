# MyPlans Audit Service

Microservicio de auditoría para la plataforma MyPlans.
Mantiene un registro **inmutable y append-only** de todos los cambios de estado
de TAGs realizados por los usuarios. Ningún registro puede ser editado ni
eliminado — solo se pueden agregar nuevos eventos y consultarlos.

---

## Stack

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 17 |
| Framework | Spring Boot 3.2 |
| Base de datos | MySQL 8 (`db_myplans_auditoria`) |
| Seguridad | JWT (HS384) + token interno `X-Internal-Token` |
| Documentación | SpringDoc OpenAPI 3 |

---

## Cómo correr

```bash
cd audit
./mvnw spring-boot:run
```

El perfil `dev` se activa por defecto. El servicio queda escuchando en
`http://localhost:8082`.

**Requisitos previos:**
- MySQL 8 corriendo en `localhost:3306`
- La base de datos `db_myplans_auditoria` se crea automáticamente con
  `createDatabaseIfNotExist=true`

---

## Perfiles

| Perfil | Datasource | DDL |
|---|---|---|
| `dev` | `localhost:3306/db_myplans_auditoria` (root, sin contraseña) | `update` |
| `prod` | Variables de entorno `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `update` |

---

## Endpoints

Base path: `/api/v1/historial`

| Método | Ruta | Autorización | Descripción |
|---|---|---|---|
| `POST` | `/api/v1/historial` | `CORE_SERVICE` (token interno) | Registrar un evento de cambio de estado |
| `GET` | `/api/v1/historial` | `ROLE_AUDITOR`, `ROLE_ADMIN` | Listar todos los eventos (más reciente primero) |
| `GET` | `/api/v1/historial/tag/{idTag}` | `ROLE_AUDITOR`, `ROLE_ADMIN` | Historial de un TAG específico |
| `GET` | `/api/v1/historial/tag/{idTag}/count` | `ROLE_AUDITOR`, `ROLE_ADMIN` | Conteo de eventos de un TAG |

Swagger UI disponible en `http://localhost:8082/swagger-ui.html`

---

## Seguridad

El servicio maneja dos tipos de autenticación:

**JWT (usuarios):** El `JwtAuthFilter` valida el token Bearer en cada request.
Los roles `ROLE_AUDITOR` y `ROLE_ADMIN` pueden consultar el historial.

**Token interno (`X-Internal-Token`):** El endpoint `POST /api/v1/historial`
solo puede ser invocado por el Core Service a través del header
`X-Internal-Token`. Al validarlo, el filtro asigna el rol virtual
`CORE_SERVICE`. Los usuarios humanos no pueden publicar eventos directamente.

---

## Modelo de datos

Tabla: `HISTORIAL`

| Columna | Tipo | Descripción |
|---|---|---|
| `id_historial` | `BIGINT` PK | Identificador del evento |
| `id_tag` | `INT` NOT NULL | TAG que cambió de estado |
| `id_usuario` | `INT` NOT NULL | Usuario que realizó el cambio |
| `estado_anterior` | `VARCHAR(50)` | Estado previo (null en el primer registro) |
| `estado_nuevo` | `VARCHAR(50)` NOT NULL | Estado resultante |
| `observaciones` | `TEXT` | Comentario opcional |
| `fecha_actualizado` | `DATETIME` NOT NULL | Timestamp del evento (no editable) |

El diseño es **append-only**: no existen endpoints `PUT` ni `DELETE`.
Una vez registrado, un evento no puede modificarse.

---

## Variables de entorno

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `JWT_SECRET` | (valor dev hardcodeado) | Secreto compartido con todos los servicios |
| `AUDIT_INTERNAL_TOKEN` | `dev-internal-token-please-change-in-prod` | Token que usa el Core para publicar eventos |
| `DB_HOST` | — | Host MySQL (prod) |
| `DB_PORT` | `3306` | Puerto MySQL (prod) |
| `DB_NAME` | `db_myplans_auditoria` | Nombre de la base de datos (prod) |
| `DB_USER` | `admin` | Usuario MySQL (prod) |
| `DB_PASSWORD` | — | Contraseña MySQL (prod) |

> En producción, cambiar `AUDIT_INTERNAL_TOKEN` y `JWT_SECRET` por valores seguros.

---

## Estructura del proyecto

```
audit/
├── src/main/java/com/myplans/audit/
│   ├── controller/        # HistorialController — endpoints REST
│   ├── dto/               # HistorialRequestDTO, HistorialResponseDTO
│   ├── entity/            # Historial (entidad JPA)
│   ├── repository/        # HistorialRepository
│   ├── service/           # HistorialService
│   ├── security/          # JWT filter, token interno, SecurityConfig
│   └── exception/         # GlobalExceptionHandler, excepciones de negocio
└── src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    └── application-prod.yml
```
