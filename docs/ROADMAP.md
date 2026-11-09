# Hoja de ruta

Se avanza por fases con aprobación entre ellas. Una fase no se considera terminada por compilar: debe funcionar de extremo a extremo y tener pruebas.

| Fase | Alcance | Estado |
|---|---|---|
| **0. Fundación** | Configuración Android, arquitectura, navegación, tema, Room, Hilt, pruebas básicas | **Completada y aprobada** |
| **1. Captura e Inbox** | Captura rápida, ficha completa, bandeja, procesamiento, plantillas, autoguardado | **Completada y aprobada** |
| **2. Tareas y prioridad** | CRUD, estados, fechas, prioridad, búsqueda, filtros, subtareas, dependencias | **Completada y aprobada** |
| **3. Dashboard** | Indicadores, tres prioridades, alertas, próximas 24 horas | **Aprobada; en pausa antes de implementar**. Plan abajo |
| **4. Proyectos y planificación** | Proyectos, hitos, bloques de tiempo, planificación semanal | Pendiente |
| **5. Revisión y formación** | Revisiones diarias y semanales, formación, métricas | Pendiente |
| **6. Estabilización** | QA, rendimiento, seguridad, copias de respaldo, APK, documentación | Pendiente |
| **Futura. Integraciones** | Gmail, Calendar, Outlook, sincronización, funciones inteligentes | Sin planificar |

## Plan de Sprint 3 (rama `sprint-3/dashboard`)

Pausado el 2026-11-08 por el límite de uso, sin código del dashboard escrito. Sustituye el `PendingSection` de `HomeRoute`.

**Dominio (`domain/dashboard`, funciones puras con pruebas).** Trabaja sobre `TaskQueries.rank`.

| Elemento | Definición |
|---|---|
| Pendientes | Tareas abiertas: `PENDING`, `IN_PROGRESS`, `WAITING`, `BLOCKED` |
| Vencidas, en progreso, bloqueadas | Mismas reglas que las vistas de `TaskQueries` |
| Críticas, alta prioridad | Vistas `CRITICAL` y `HIGH`; las obligaciones cuentan como críticas |
| Completadas en la semana | `completedAt` de lunes a domingo, en la zona del `Clock` |
| Tiempo estimado | Minutos de tareas abiertas con fecha entre hoy y el domingo |
| Tiempo disponible | Requiere capacidad y bloques: mostrar que llega en la Fase 4, sin inventar un valor |

**Prioridades del día.** Excluir `BLOCKED`, `WAITING` y tareas con dependencias abiertas, salvo obligaciones. Primero las fijadas por el usuario; después, hasta completar tres, por: emergencia (obligación con fecha vencida o en 7 días, o crítica vencida o de hoy), nivel y fecha. Las emergencias adicionales se añaden aunque superen tres. El motivo usa `priorityExplanation`.

**Selección del usuario.** `DailyFocusRepository` en DataStore: fecha, fijadas y descartadas; caduca al cambiar el día.

**Alertas, solo si hay casos.** Compromisos vencidos (fecha límite u obligación), compromisos en 7 días, bloqueadas, dependencias pendientes y críticas sin siguiente acción. La sobrecarga de calendario y los proyectos con retraso requieren la Fase 4.

**Próximas 24 horas.** Tareas abiertas con fecha de hoy o mañana; los bloques de tiempo se suman en la Fase 4.

**Navegación.** Convertir `TasksRoute` en `data class TasksRoute(val view: String? = null)` y leer `view` del `SavedStateHandle` en `TasksViewModel`, para que indicadores y alertas abran su vista.

**Al retomar:** instalar en el teléfono la corrección de zona horaria, que aún no está en la app instalada.

## Decisiones sobre vacíos del brief

**Recordatorios.** La sección 13 y el criterio 14 del MVP los exigen, pero la sección 19 no los asigna a ninguna fase. Se añadirán tras la Fase 3, porque solo dependen de tareas y fechas; es también el momento de integrar WorkManager.

## Backlog técnico

| Elemento | Motivo |
|---|---|
| Actualizar Android Studio a Rabbit 2 (2026.2.2) | Narwhal 2025.1.1 no puede abrir un proyecto con AGP 9.5 |
| Generar firma de release | Necesaria para la APK final en la Fase 6; las claves nunca van al repositorio |
| Decidir adjuntos locales de la ficha | Requieren permisos persistentes de archivos (D-034) |
| Evaluar Room 3 | La línea 2.8 es la elegida por madurez |
| Evaluar `targetSdk` 37 | Requiere una imagen de sistema API 37 para probar el comportamiento |
