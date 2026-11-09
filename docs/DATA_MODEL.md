# Modelo de datos

Base local Room, archivo `executive_os.db`. Los esquemas se exportan a `app/schemas/` y se versionan en Git: son el contrato contra el que se prueban las migraciones.

## Convenciones

| Aspecto | Regla |
|---|---|
| Identificador | UUID como `TEXT PRIMARY KEY` |
| Instantes | `java.time.Instant`, guardado como milisegundos UTC (`INTEGER`) |
| Fechas sin hora | `java.time.LocalDate`, guardado como texto ISO (`AAAA-MM-DD`) |
| Enumeraciones | Texto con el nombre del valor |
| Columnas | `snake_case` |
| Fechas ausentes | `NULL`. **Nunca se inventa un valor** |
| Puntuaciones ausentes | `NULL`. Un campo vacío no se interpreta como un valor |
| Conflictos | `ABORT` por defecto. No se usa `REPLACE` sobre datos del usuario |
| Eliminación | Se prefiere archivar. El borrado físico requiere confirmación |
| Tiempos | Sin valores negativos: el formulario solo admite dígitos y `TaskValidator` lo comprueba |

**Política de migraciones:** cada cambio de esquema sube `SCHEMA_VERSION`, versiona su JSON y tiene prueba sobre SQLite real. Los cambios puramente aditivos usan `AutoMigration`, que Room verifica al compilar; los demás, una migración manual. No se usa `fallbackToDestructiveMigration`: si falta una migración, la app falla de forma visible en lugar de borrar datos.

## Esquema actual: versión 2

| Tabla | Propósito | Relaciones |
|---|---|---|
| `categories` | Catálogo, con las 7 del brief | |
| `inbox_items` | Captura original e inmutable y su estado de procesamiento | `category_id` → `categories` (SET NULL) |
| `tasks` | Ficha completa, secciones A a I | `category_id`, `project_id`, `source_inbox_item_id` (SET NULL) |
| `projects` | Datos mínimos para convertir capturas; se amplía en Fase 4 | `source_inbox_item_id`, sin clave foránea |
| `notes` | Capturas convertidas en nota | `source_inbox_item_id`, sin clave foránea |
| `capture_templates` | Plantillas editables, con las 10 del brief | `category_id` → `categories` (SET NULL) |
| `tags`, `task_tags` | Etiquetas, relación muchos a muchos | CASCADE en `task_tags` |
| `audit_log` | Historial de acciones sobre capturas | Sin claves foráneas, para sobrevivir a lo que registra |

### Ciclo de una captura

Cada captura crea, en una misma transacción, una fila en `inbox_items` con el contenido original y otra en `tasks` en estado `INBOX`. Editar la ficha modifica solo `tasks`, de modo que el original nunca se pierde.

| Acción | `inbox_items.state` | `tasks.status` | Efecto adicional |
|---|---|---|---|
| Capturar | `PENDING` | `INBOX` | |
| Convertir en tarea | `PROCESSED` | `PENDING` | |
| Delegar | `PROCESSED` | `WAITING` | Asigna responsable y marca `DELEGATED` |
| Convertir en proyecto | `PROCESSED` | `ARCHIVED` | Crea `projects` con referencia a la captura |
| Convertir en nota | `PROCESSED` | `ARCHIVED` | Crea `notes` con referencia a la captura |
| Posponer | `POSTPONED` | Sin cambio | Vuelve a Pendientes al llegar `postponed_until` |
| Archivar | `ARCHIVED` | `ARCHIVED` | Se conserva todo el contenido |
| Eliminar | Se borra | Se borra | Requiere confirmación; queda el registro en `audit_log` |

**Doble pulsación:** el identificador de la captura se genera con el borrador, y `inbox_items` ignora un identificador repetido. Un segundo envío no puede crear otra fila.

**Fechas de la captura:** se guardan como `target_date` salvo que el usuario confirme que son una fecha límite, en cuyo caso van a `deadline_date`. «Esta semana» corresponde al domingo de la semana en curso.

### Datos iniciales

Las categorías y las plantillas se siembran en `onCreate`, que Android envuelve en una transacción. Usan UUID fijos para que una copia restaurada en otra instalación siga apuntando a la misma fila. Quien ya tenía la versión 1 recibe las plantillas durante la migración.

## Entidades pendientes

| Entidad | Fase | Notas |
|---|---|---|
| `TaskAttachmentEntity` | Por decidir | Adjuntos locales; requieren permisos persistentes de archivos |
| `UserSettingsEntity` | 2 | Pesos, umbrales y capacidad |
| `TaskDependencyEntity` | 2 | Impide dependencias circulares desde dominio |
| `ReminderEntity` | Por asignar | Ver [ROADMAP.md](ROADMAP.md) |
| `ProjectMilestoneEntity` | 4 | El progreso usa hitos y entregables ponderados |
| `TimeBlockEntity` | 4 | Detección de solapamientos |
| `ReviewEntity` | 5 | Respuestas de revisiones diarias y semanales |
| `LearningGoalEntity`, `LearningSessionEntity` | 5 | Horas por semana y tema |

## Relaciones objetivo pendientes

- Una tarea puede tener subtareas, mediante referencia a su tarea padre (Fase 2).
- Una tarea puede depender de otras tareas, sin ciclos (Fase 2).

## Ampliaciones al brief

La sección 14 no incluía tablas para dos funciones exigidas en otras secciones. Se añadieron en Sprint 1 (D-029):

| Función exigida | Tabla añadida |
|---|---|
| Convertir una captura en nota (4.6, 4.8) | `notes` |
| Plantillas editables (4.7) | `capture_templates` |

Sigue abierto para la Fase 4 si los riesgos y entregables de proyecto requieren tabla propia o si basta con ampliar los hitos con tipo y peso.
