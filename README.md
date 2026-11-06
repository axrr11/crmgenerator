# CRM Generator – Spring Boot (Java 21)

Port del generador dinámico de CRMs (Laravel) a **Spring Boot 3.3 + Java 21**.

## Diferencia de enfoque

En Laravel se generaban archivos PHP en caliente. En Java no se pueden cargar clases nuevas sin reiniciar, así que este port:

1. **Crea tablas dinámicamente** con JDBC (`CREATE TABLE crm_*`)
2. **Guarda el meta-esquema** en `crm_modules` (campos, tipo, métricas)
3. **CRUD genérico en runtime** (`DynamicCrudService`) usando solo metadatos + SQL parametrizado

El resultado funcional es el mismo: defines un módulo en el constructor y en segundos tienes listado, alta, edición, detalle, métricas y ventas.

## Stack

- Spring Boot 3.3, Spring Security, Spring Data JPA, Thymeleaf
- MySQL 8 (Docker) o H2 opcional
- Chart.js en métricas

## Arranque rápido (Docker)

```bash
docker compose up
# primera vez baja dependencias Maven (puede tardar)
```

Abrir: http://localhost:8080/login

```
Email: admin@crm.local
Password: password
```

## Arranque local

```bash
# MySQL en localhost:3306, user crm / secret, db crm_generator
mvn spring-boot:run
```

## Funcionalidades portadas

| Feature | Estado |
|---------|--------|
| Constructor de módulos | Sí |
| Tipos: Catálogo, Kanban, Form, Metrics, Sales | Sí |
| Companion métricas al crear Ventas | Sí |
| CRUD dinámico + soft delete | Sí |
| Búsqueda / orden (whitelist columnas) | Sí |
| Login + roles (ADMIN/SELLER/VIEWER) | Sí |
| Gestión de usuarios | Sí |
| Dashboard | Sí |
| Pagos (entidad + detalle ventas) | Parcial (listado; alta vía servicio) |
| Validación min≥0, SQL parametrizado | Sí |
| Actividades / webhooks / export-clean | No (extensible) |

## Estructura

```
com.crmgenerator
├── generator/          # Motor + DTOs + enums + Identifier
├── domain/             # User, CrmModule, Activity, Payment
├── service/            # DynamicCrudService
├── security/           # SecurityConfig
└── web/                # Controllers + admin
```

## Seguridad

- Columnas y tablas validadas con `Identifier` / regex
- Queries con `PreparedStatement` / argumentos JDBC
- Números con `min:0` en formularios y rechazo de negativos en servicio
- Constructor solo rol `ADMIN`

## Licencia

Uso interno / plantilla de entrega a clientes.


## Caché Redis

Listados, búsquedas y conteos de módulos se cachean en Redis (`moduleSearch`, `moduleCount`).
Lookups de clientes/productos en `lookups`.

- TTL por defecto: **120 segundos** (`crm.cache.search-ttl-seconds`)
- Al crear / editar / eliminar se invalida la caché del módulo
- Docker Compose incluye el servicio `redis`

Desactivar:

```yaml
crm.cache.enabled: false
```
