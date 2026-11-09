# Visión de producto

## Qué es

EXECUTIVE OS es un sistema personal de dirección y ejecución para Android. Organiza responsabilidades laborales, académicas, profesionales, de proyectos y personales de una persona con muchos compromisos simultáneos. No es una lista de tareas: conserva el contexto, la importancia y las consecuencias de cada compromiso, y ayuda a convertirlo en una acción concreta.

## Las nueve preguntas que debe responder cada día

1. ¿Qué tengo pendiente?
2. ¿Qué es realmente importante?
3. ¿Qué debo hacer primero?
4. ¿Qué puede esperar?
5. ¿Qué está en riesgo de retrasarse?
6. ¿Qué está bloqueado?
7. ¿Qué debo hacer concretamente para avanzar?
8. ¿Estoy distribuyendo bien mi tiempo?
9. ¿Estoy avanzando en mis objetivos profesionales y personales?

## Principios

| Preferimos | Antes que |
|---|---|
| Claridad | Complejidad |
| Ejecución | Administración |
| Captura rápida | Clasificación perfecta |
| Prioridades fundamentadas | Listas interminables |
| Información contextual | Títulos aislados |
| Automatización controlada | Decisiones automáticas opacas |
| Simplicidad visual | Saturación de indicadores |
| Persistencia y confiabilidad | Funcionalidades decorativas |

La aplicación no debe exigir más tiempo del que ahorra. Registrar una tarea debe ser extraordinariamente sencillo y, aun así, conservar contexto, importancia, consecuencias y siguiente acción.

## Compromisos de producto

- **Offline-first.** La base de datos local es la fuente de verdad; el MVP funciona sin conexión.
- **Privado por diseño.** Sin publicidad ni seguimiento. El MVP no envía datos personales a ningún servidor; la APK no declara permiso de internet.
- **Transparente.** Las prioridades muestran cómo se calcularon. Las sugerencias nunca modifican datos sin confirmación. Las recomendaciones heurísticas se presentan como tales, no como hechos.
- **Honesto con el avance.** Una función solo se declara implementada cuando funciona de extremo a extremo y tiene pruebas.

## Fuera del MVP

Gmail, Google Calendar, Outlook, Google Tasks, sincronización multidispositivo, copias en la nube e inteligencia artificial. La arquitectura las deja posibles, pero no se implementan salvo petición expresa.

El detalle funcional completo y la trazabilidad están en [REQUIREMENTS.md](REQUIREMENTS.md).
