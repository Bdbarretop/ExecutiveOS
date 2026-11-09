# UX de captura

> **Estado:** implementada en Sprint 1. El requisito íntegro está en la sección 4 de [REQUIREMENTS.md](REQUIREMENTS.md).

## Objetivo de diseño

Capturar debe costar lo mínimo, sin perder lo que hace útil a una tarea: qué hay que hacer, por qué importa, cuál es el resultado esperado y qué pasa si no se hace. La solución es **captura progresiva**: guardar primero, completar después.

| Modalidad | Tiempo objetivo | Uso |
|---|---|---|
| Captura rápida | 15 a 30 segundos | Registrar una idea, incluso durante una reunión |
| Ficha completa | 1 a 2 minutos | Documentar una responsabilidad compleja |

Los tiempos son objetivos de diseño y **aún no se han medido** con pruebas de usabilidad.

## Captura rápida

Se abre desde el botón flotante **CAPTURAR**, disponible en las pantallas principales.

| Campo | Comportamiento |
|---|---|
| Título o descripción breve | **Único campo obligatorio**; recibe el foco al abrir |
| Categoría | Recuerda la última usada |
| Fecha | Hoy, Mañana, Esta semana, Elegir fecha, Sin fecha definida |
| ¿Es fecha límite? | Aparece solo con fecha elegida. Sin marcar, se guarda como fecha objetivo |
| Detalles | Opcional, se despliega al pedirlo |
| Plantillas | Completan lo vacío; **nunca sobrescriben lo ya escrito** |

Comportamiento al guardar:

- **GUARDAR CAPTURA** guarda de inmediato y deja el formulario listo para otra captura, conservando la categoría.
- La confirmación es discreta y el contenido entra en la **Bandeja**.
- Una doble pulsación no crea duplicados.
- El borrador se guarda solo y sobrevive a un cierre accidental.
- Si falla el guardado, el texto sigue en pantalla y se avisa.

Si el título se parece al de una tarea abierta, aparece un aviso «Posible duplicado de», sin bloquear nada. Compara palabras significativas, sin distinguir mayúsculas ni tildes.

## Divulgación progresiva

Cuatro accesos guardan la captura y abren la ficha solo por lo que hace falta:

| Acción | Abre |
|---|---|
| Añadir contexto | Sección B |
| Evaluar importancia | Secciones C y F |
| Definir siguiente acción | Sección E |
| Más opciones | Secciones D, G, H e I |

## Ficha completa

Nueve secciones plegables. Ningún campo salvo el título es obligatorio, y la ficha se guarda sola.

| Sección | Contenido |
|---|---|
| A. Identificación | Título, descripción, categoría, proyecto, etiquetas |
| B. Contexto | Qué hacer, por qué, quién lo solicitó, origen, qué recordar, documentos |
| C. Importancia | Impacto, consecuencias de no hacerlo o de hacerlo tarde, afectados, riesgo |
| D. Resultado esperado | ¿Cómo sabré que está terminada? |
| E. Siguiente acción | Paso concreto |
| F. Priorización | Impacto, urgencia y riesgo de retraso, de 1 a 5, con su significado |
| G. Fechas y tiempo | Creación, inicio, objetivo, límite, duración estimada y real |
| H. Responsables | Responsable, involucrados, dependencias, delegación |
| I. Documentación | Notas, enlaces, referencias |

La ficha señala qué campos clave faltan, valida el orden de las fechas y sugiere dividir las tareas de más de 4 horas. Además muestra su estado, la prioridad con su explicación, las subtareas, las dependencias y el historial.

## Bandeja

Tres pestañas: **Pendientes**, **Pospuestas** y **Procesadas**. Cada captura permite abrir la ficha, convertir en tarea, proyecto o nota, delegar, posponer, archivar y eliminar, esta última con confirmación.

Mantener pulsada una captura activa el **procesamiento por lotes**: convertir en tareas, posponer, archivar o eliminar varias a la vez, todas en una transacción.

Las capturas pospuestas vuelven solas a Pendientes en su fecha. Las convertidas o archivadas siguen en Procesadas con su contenido original.

## Plantillas

Las 10 iniciales del brief se editan en **Más → Configuración → Plantillas de captura** o con «Gestionar» desde la captura.

## Pendiente

- Medir los tiempos objetivo con pruebas de usabilidad.
- Adjuntos locales y campo de recordatorio (D-034).
