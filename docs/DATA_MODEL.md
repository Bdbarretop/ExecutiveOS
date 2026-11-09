# Modelo de datos

Base local Room, archivo `executive_os.db`. Los esquemas se exportan a `app/schemas/` y se versionan en Git: son el contrato contra el que se prueban las migraciones.

## Convenciones

| Aspecto | Regla |
|---|---|
| Identificador | UUID como `TEXT PRIMARY KEY` |
| Instantes | `java.time.Instant`, guardado como milisegundos UTC (`INTEGER`) |
| Fechas sin hora | `java.time.LocalDate`, guardado como texto ISO (`AAAA-MM-DD`) |
| Columnas | `snake_case` |
| Fechas ausentes | `NULL`. **Nunca se inventa un valor** |
| Puntuaciones ausentes | `NULL`. Un campo vacío no se interpreta como un valor |
| Conflictos | `ABORT` por defecto. No se usa `REPLACE` sobre datos del usuario |
| Eliminación | Se prefiere archivar. El borrado físico requiere confirmación |
| Tiempos | Sin valores negativos: validación en dominio y restricción `CHECK` cuando Room lo permita |

**Política de migraciones:** cada cambio de esquema sube `SCHEMA_VERSION` y añade una migración explícita con su prueba. No se usa `fallbackToDestructiveMigration`: si falta una migración, la app falla de forma visible en lugar de borrar datos.

## Esquema actual: versión 1

### `categories`

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `TEXT` | Clave primaria (UUID) |
| `name` | `TEXT` | No nulo, **único** |
| `sort_order` | `INTEGER` | No nulo |
| `is_system` | `INTEGER` | No nulo, booleano |
| `created_at` | `INTEGER` | No nulo, milisegundos UTC |

Índice único: `index_categories_name`.

Se siembran las siete categorías del brief (Trabajo, Estudio, Proyecto, Personal, Finanzas, Salud, Otro) al crear la base, con UUID fijos. La siembra se ejecuta en `onCreate`, que Android envuelve en una transacción, y no se repite al reabrir. Los UUID fijos permiten que una copia restaurada en otra instalación apunte a la misma categoría.

## Entidades planificadas

Se añaden junto con el módulo que las usa, cada una con su migración.

| Entidad | Fase | Notas |
|---|---|---|
| `CategoryEntity` | 0 | **Implementada** |
| `InboxItemEntity` | 1 | Conserva el contenido original tras la conversión |
| `TaskEntity` | 1 | Campos de la ficha completa; referencia opcional a la captura de origen |
| `TaskAttachmentEntity` | 1 | Adjuntos locales |
| `TagEntity`, `TaskTagCrossRef` | 1 | Relación muchos a muchos |
| `AuditLogEntity` | 1 | Historial. Sin clave foránea para sobrevivir a la entidad auditada |
| `UserSettingsEntity` | 2 | Pesos, umbrales y capacidad |
| `TaskDependencyEntity` | 2 | Impide dependencias circulares desde dominio |
| `ReminderEntity` | Por asignar | Ver [ROADMAP.md](ROADMAP.md) |
| `ProjectEntity`, `ProjectMilestoneEntity` | 4 | El progreso usa hitos y entregables ponderados |
| `TimeBlockEntity` | 4 | Detección de solapamientos |
| `ReviewEntity` | 5 | Respuestas de revisiones diarias y semanales |
| `LearningGoalEntity`, `LearningSessionEntity` | 5 | Horas por semana y tema |

## Relaciones objetivo

- Un proyecto tiene muchas tareas.
- Una tarea puede tener subtareas, mediante referencia a su tarea padre.
- Una tarea puede depender de otras tareas, sin ciclos.
- Una tarea puede tener varias etiquetas.
- Una captura puede convertirse en tarea o proyecto; el destino conserva la referencia a la captura original.

## Vacíos del brief por resolver

La lista de la sección 14 no cubre tres funciones exigidas en otras secciones. Hay que decidirlas antes de cerrar el esquema de Sprint 1:

| Función exigida | Problema | Propuesta |
|---|---|---|
| Convertir una captura en nota (4.6, 4.8) | Ninguna entidad guarda notas independientes | Añadir `NoteEntity`, enlazable a tarea o proyecto |
| Plantillas editables (4.7) | Ninguna entidad las guarda | Añadir `CaptureTemplateEntity`, sembrada con las 10 iniciales |
| Riesgos y entregables de proyecto (8) | Solo existe `ProjectMilestoneEntity` | Añadir tipo y peso al hito para entregables; decidir en Fase 4 si los riesgos requieren entidad propia |
