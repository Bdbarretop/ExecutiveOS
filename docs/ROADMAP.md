# Hoja de ruta

Se avanza por fases con aprobación entre ellas. Una fase no se considera terminada por compilar: debe funcionar de extremo a extremo y tener pruebas.

| Fase | Alcance | Estado |
|---|---|---|
| **0. Fundación** | Configuración Android, arquitectura, navegación, tema, Room, Hilt, pruebas básicas | **Completada y aprobada** |
| **1. Captura e Inbox** | Captura rápida, ficha completa, bandeja, procesamiento, plantillas, autoguardado | **Completada y aprobada** |
| **2. Tareas y prioridad** | CRUD, estados, fechas, prioridad, búsqueda, filtros, subtareas, dependencias | **Completada**, pendiente de aprobación. Resultados en [TEST_PLAN.md](TEST_PLAN.md) |
| **3. Dashboard** | Indicadores, tres prioridades, alertas, próximas 24 horas | Pendiente |
| **4. Proyectos y planificación** | Proyectos, hitos, bloques de tiempo, planificación semanal | Pendiente |
| **5. Revisión y formación** | Revisiones diarias y semanales, formación, métricas | Pendiente |
| **6. Estabilización** | QA, rendimiento, seguridad, copias de respaldo, APK, documentación | Pendiente |
| **Futura. Integraciones** | Gmail, Calendar, Outlook, sincronización, funciones inteligentes | Sin planificar |

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
