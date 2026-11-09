# UX de captura

> **Estado:** especificación para Sprint 1. En Sprint 0 solo existe la navegación hasta una pantalla que indica que la captura llega en la Fase 1. El requisito íntegro está en la sección 4 de [REQUIREMENTS.md](REQUIREMENTS.md).

## Objetivo de diseño

Capturar debe costar lo mínimo, sin perder lo que hace útil a una tarea: qué hay que hacer, por qué importa, cuál es el resultado esperado y qué pasa si no se hace. La solución es **captura progresiva**: guardar primero, completar después.

| Modalidad | Tiempo objetivo | Uso |
|---|---|---|
| Captura rápida | 15 a 30 segundos | Registrar una idea, incluso durante una reunión |
| Ficha completa | 1 a 2 minutos | Documentar una responsabilidad compleja |

Los tiempos son objetivos de diseño y deben verificarse con pruebas de usabilidad; no son garantías.

## Captura rápida

Se abre desde el botón flotante **CAPTURAR**, disponible en las pantallas principales.

| Campo | Regla |
|---|---|
| Título o descripción breve | **Único campo obligatorio** |
| Categoría | Trabajo, Estudio, Proyecto, Personal, Finanzas, Salud, Otro. Recuerda la última usada |
| Fecha | Hoy, Mañana, Esta semana, Elegir fecha, Sin fecha definida |
| Detalles | Opcional; abre la ampliación de la ficha |

Acción principal: **GUARDAR CAPTURA**.

Reglas:

- Guardar es inmediato y no exige prioridad, proyecto, fecha ni descripción.
- Los borradores se autoguardan y sobreviven a un cierre accidental.
- Una doble pulsación no crea duplicados.
- La confirmación es discreta y permite capturar otra tarea enseguida.
- La fecha solo se interpreta como **fecha de vencimiento si el usuario lo confirma**. Una fecha objetivo no equivale a una obligación formal.

Toda captura rápida entra primero en la **bandeja de entrada**.

## Divulgación progresiva

La vista inicial no muestra todos los campos. Cuatro accesos amplían la ficha:

| Acción | Abre |
|---|---|
| Añadir contexto | Sección B |
| Evaluar importancia | Secciones C y F |
| Definir siguiente acción | Sección E |
| Más opciones | Secciones restantes |

## Ficha completa

| Sección | Contenido |
|---|---|
| A. Identificación | Título, descripción, categoría, proyecto, etiquetas |
| B. Contexto | Qué hacer, por qué, quién lo solicitó, de dónde proviene, qué recordar, qué documentos se necesitan; notas y enlaces |
| C. Importancia | Impacto esperado, consecuencia de no hacerlo o de hacerlo tarde, personas o áreas afectadas, riesgo relacionado |
| D. Resultado esperado | ¿Cómo sabré que está terminada? Evita tareas ambiguas |
| E. Siguiente acción | Paso concreto, **campo destacado**. Sugiere dividir en subtareas si la tarea es grande |
| F. Priorización | Impacto, urgencia y riesgo de retraso, de 1 a 5, con explicación breve de cada nivel |
| G. Fechas y tiempo | Creación, inicio, objetivo, límite, duración estimada y real, recordatorio. Objetivo y límite son distintos. Nunca se inventan fechas |
| H. Responsables | Responsable principal, personas involucradas, dependencias, estado de delegación |
| I. Documentación | Notas, enlaces, referencias y adjuntos locales cuando sean compatibles |

Ningún campo salvo el título es obligatorio.

## Inteligencia de captura (sin IA)

Recordar última categoría y proyecto, sugerir plantillas, detectar campos vacíos, mostrar tareas similares, identificar posibles duplicados por texto, convertir una tarea en proyecto o una captura en nota, y delegar. **Las sugerencias nunca modifican datos sin confirmación.**

## Plantillas iniciales (editables)

Preparar informe, Revisar documento, Responder solicitud, Preparar reunión, Realizar análisis, Estudiar tema, Completar formación, Hacer seguimiento, Resolver incidencia, Actividad personal.

## Bandeja de entrada

Abrir, editar, completar contexto, convertir en tarea, proyecto o nota, delegar, archivar, eliminar y posponer su procesamiento. Incluye procesamiento por lotes. **Convertir una captura nunca pierde su contenido original.**

## Decisiones abiertas para Sprint 1

1. **Conversión en nota.** Ninguna de las 16 entidades listadas almacena notas independientes. Ver [DATA_MODEL.md](DATA_MODEL.md).
2. **Semántica de «Esta semana».** Falta definir si significa domingo o último día laboral, y cómo pedir la confirmación de vencimiento sin añadir pasos.
3. **Almacén de borradores.** Propuesta: DataStore para el borrador en curso, porque es estado de dispositivo y no debe viajar en las copias de seguridad.
4. **Protección ante doble pulsación.** Propuesta: desactivar el envío mientras se guarda y usar un UUID generado al abrir el formulario, de modo que un segundo intento no pueda crear otro registro.
