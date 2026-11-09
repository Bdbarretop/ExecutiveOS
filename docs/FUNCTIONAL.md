# Documentación funcional

Describe qué hace cada módulo y con qué reglas. El detalle técnico está en [ARCHITECTURE.md](ARCHITECTURE.md) y [DATA_MODEL.md](DATA_MODEL.md); los motivos, en [DECISIONS.md](DECISIONS.md).

## Captura y Bandeja

La captura guarda en un paso una fila inmutable con el texto original y una tarea en estado `INBOX`. Repetir el envío no duplica. Desde la Bandeja se convierte en tarea, proyecto o nota, se delega, se pospone, se archiva o se elimina con confirmación.

Las fechas capturadas son **objetivo** salvo que la persona confirme que son **límite**.

## Tareas

| Regla | Comportamiento |
|---|---|
| Estados | 8 estados; `TaskStatusRules` valida cada transición |
| Fechas | Una fecha límite es un compromiso y no se pospone sola; posponer mueve la fecha objetivo |
| Dependencias | No se admiten ciclos, directos ni indirectos |
| Subtareas | Heredan categoría y proyecto |
| Historial | Cada cambio relevante queda registrado |

## Motor de prioridad

Puntuación ponderada de impacto, urgencia y riesgo de retraso, con pesos y umbrales configurables, en aritmética entera. Si falta una escala, el resultado es **Sin evaluar**. Una prioridad manual exige motivo y no oculta la calculada. Las obligaciones críticas se señalan aunque la puntuación sea baja.

## Inicio

- **Prioridades de hoy:** hasta tres recomendaciones, sin forzar a llegar a tres. Una emergencia se añade aunque supere ese número. Se pueden fijar o quitar por el día.
- **Alertas:** solo las que tienen casos: compromisos vencidos y próximos, bloqueos, dependencias, críticas sin siguiente acción, días sobrecargados y proyectos con retraso.
- **Tiempo:** planificado y disponible de hoy al domingo.
- **Próximas 24 horas:** bloques en curso o próximos y tareas de hoy o mañana.

## Proyectos

Progreso = peso de hitos y entregables completados sobre el peso total; sin hitos no hay porcentaje. La fecha prevista es la del último hito abierto o, si no hay fechas, la objetivo. Un proyecto está retrasado si, sin haber terminado, tiene hitos vencidos, superó su objetivo o su previsión lo rebasa.

## Planificación

Cuatro tipos de bloque. La capacidad es minutos por día laborable por porcentaje objetivo, inicialmente 8 horas y 75 %. El tiempo personal no cuenta como productivo y un día no laborable no se sobrecarga. Los solapamientos avisan pero no impiden guardar. Mover un bloque conserva su duración y, al cambiar de día, su hora local.

## Revisiones

Una por tipo y periodo; la semanal se identifica por su lunes. El resumen semanal se calcula con datos reales y sus recomendaciones son heurísticas declaradas, que solo aparecen con datos que las respalden.

## Formación

Objetivos con tema, curso, materiales, evidencias, nivel de dominio y fecha objetivo. Las sesiones registran fecha, minutos, notas y evaluación. Horas por tema y por semana en el periodo elegido. Los 12 temas iniciales son sugerencias ampliables.

## Métricas

| Indicador | Definición |
|---|---|
| Prioridades completadas | Críticas y altas con fecha o cierre en el periodo que están completadas |
| Cumplimiento de fechas | Fechas ya alcanzadas en el periodo que se cumplieron a tiempo; excluye subtareas y prioridad baja |
| Vencidas y bloqueadas | Situación a día de hoy |
| Estimado frente a real | Solo tareas completadas con ambos valores |
| Trabajo profundo | Tiempo real en bloques de ese tipo |
| Formación | Tiempo de las sesiones del periodo |
| Progreso de proyectos | Avance ponderado de los proyectos en curso |
| Tiempo por categoría | Tiempo real de las tareas completadas |

No hay puntuación global de productividad. Sin datos se indica «Sin datos», no 0 %.

## Recordatorios

Locales con WorkManager: puntuales, compromisos, tareas críticas y revisiones. Horario silencioso, posponer y completar desde la notificación. Sin duplicados. Su puntualidad es aproximada, por las restricciones de Android.

## Copia de seguridad

| Formato | Contenido | Restaurable |
|---|---|---|
| Copia protegida (`.eosbackup`) | Toda la base, cifrada con contraseña | Sí |
| JSON | Toda la base, legible | Sí |
| ZIP de CSV | Un CSV por tabla, para consulta | No |

Antes de restaurar, la copia se ensaya entera en una base temporal y se lee con el código real de la app. Después se muestra un resumen y se pide confirmación. El reemplazo es una sola transacción: o se aplica completo o no cambia nada. Tras restaurar se reprograman los recordatorios futuros de la copia.

No se incluyen las preferencias propias del dispositivo: tema, borrador de captura y foco del día.
