# Hoja de ruta

Se avanza por fases con aprobación entre ellas. Una fase no se considera terminada por compilar: debe funcionar de extremo a extremo y tener pruebas.

| Fase | Alcance | Estado |
|---|---|---|
| **0. Fundación** | Configuración Android, arquitectura, navegación, tema, Room, Hilt, pruebas básicas | **Completada y aprobada** |
| **1. Captura e Inbox** | Captura rápida, ficha completa, bandeja, procesamiento, plantillas, autoguardado | **Completada y aprobada** |
| **2. Tareas y prioridad** | CRUD, estados, fechas, prioridad, búsqueda, filtros, subtareas, dependencias | **Completada y aprobada** |
| **3. Dashboard** | Indicadores, tres prioridades, alertas, próximas 24 horas | **Completada y aprobada** |
| **Recordatorios** | Recordatorios locales, horario silencioso, acciones desde la notificación | **Completada y aprobada** |
| **4. Proyectos y planificación** | Proyectos, hitos, riesgos, bloques de tiempo, vistas diaria, semanal y mensual, capacidad | **Completada y aprobada** |
| **5. Revisión y formación** | Revisiones diarias y semanales, formación, métricas | **Completada y aprobada** |
| **6. Estabilización** | QA, rendimiento, seguridad, copias de respaldo, APK, documentación | **Completada**, pendiente de aprobación. Resultados en [TEST_PLAN.md](TEST_PLAN.md) |
| **Futura. Integraciones** | Gmail, Calendar, Outlook, sincronización, funciones inteligentes | Sin planificar |

## Decisiones sobre vacíos del brief

**Recordatorios.** La sección 13 y el criterio 14 del MVP los exigen, pero la sección 19 no los asigna a ninguna fase. Se implementaron tras la Fase 3, porque solo dependen de tareas y fechas.

## Backlog de evolución

| Elemento | Motivo |
|---|---|
| Actualizar Android Studio a Rabbit 2 (2026.2.2) | Narwhal 2025.1.1 no puede abrir un proyecto con AGP 9.5 |
| Crear una clave de firma propia | La APK actual usa la clave de depuración local (D-079); necesaria antes de distribuirla |
| Cifrado de la base en el dispositivo | Hoy se apoya en el almacenamiento privado de Android (D-078) |
| Bloqueo de la app con biometría | Protección adicional si el teléfono queda desbloqueado |
| Copias automáticas programadas | Hoy las inicia la persona |
| Precargar datos en las revisiones diarias | Las preguntas se responden a mano |
| Revisión con TalkBack | Solo se ha comprobado de forma automática el contraste y las etiquetas principales |
| Decidir adjuntos locales de la ficha | Requieren permisos persistentes de archivos (D-034) |
| Editar hitos y riesgos ya creados | Hoy se quita y se vuelve a añadir un hito; un riesgo solo se cierra o reabre |
| Decidir si un proyecto se puede eliminar | El brief no lo pide; hoy se marca como cancelado |
| Definir horario laboral | Permitiría consultar disponibilidad por franjas (D-054) |
| Evaluar Room 3 | La línea 2.8 es la elegida por madurez |
| Evaluar `targetSdk` 37 | Requiere una imagen de sistema API 37 para probar el comportamiento |
