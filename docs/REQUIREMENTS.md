# Requisitos

Este documento conserva la especificación original íntegra, en el anexo final, y añade su trazabilidad por fase. El prompt maestro es la fuente de verdad: si un resumen de otro documento lo contradice, prevalece el anexo.

## Trazabilidad por sección

| Sección | Tema | Fase | Estado |
|---|---|---|---|
| 2 | Decisiones técnicas y restricciones | 0 | Implantado. Ajustes en [DECISIONS.md](DECISIONS.md) |
| 3 | Diseño UX/UI | 0 y continuo | Base implantada: tema claro y oscuro, navegación, estados vacío, carga y error, botón CAPTURAR |
| 4 | Captura inteligente | 1 | Implantada, salvo adjuntos locales (D-034). Detalle en [CAPTURE_UX.md](CAPTURE_UX.md) |
| 5 | Módulo de tareas | 2 | Implantado: acciones, 8 estados, 11 vistas, filtros y búsqueda |
| 6 | Motor de priorización | 2 | Implantado: fórmula configurable, explicación, prioridad manual y obligaciones críticas |
| 7 | Dashboard ejecutivo | 3 y 4 | Implantado, con tiempo planificado y disponible, sobrecarga y proyectos con retraso |
| 8 | Proyectos | 4 | Implantado: ficha, 6 estados, hitos y entregables ponderados, riesgos, tareas y seguimiento |
| 9 | Planificación y tiempo | 4 | Implantada: vistas diaria, semanal y mensual, 4 tipos de bloque, solapamientos, capacidad y tiempo real. Disponibilidad por capacidad, no por franjas (D-054) |
| 10 | Formación | 5 | Pendiente |
| 11 | Revisiones | 5 | Pendiente |
| 12 | Analítica y métricas | 5 | Pendiente |
| 13 | Recordatorios y notificaciones | Tras la 3 | Implantados con WorkManager; puntualidad aproximada (D-049) |
| 14 | Base de datos | Progresiva | Esquema 5 implantado; el resto llega con su módulo. Ver [DATA_MODEL.md](DATA_MODEL.md) |
| 15 | Seguridad y privacidad | 0 y 6 | Base implantada: sin internet ni copias automáticas. Exportación y restauración en Fase 6 |
| 16 | Estructura del proyecto | 0 | Implantada con ajustes documentados (D-025) |
| 17 | Calidad y pruebas | Continuo | Base implantada. Ver [TEST_PLAN.md](TEST_PLAN.md) |

## Criterios de aceptación del MVP

| # | Criterio | Fase | Estado actual |
|---|---|---|---|
| 1 | La aplicación compila | 0 | Cumplido |
| 2 | Se puede instalar en Android | 0 | Cumplido en Xiaomi con Android 15 |
| 3 | La captura rápida funciona | 1 | Cumplido |
| 4 | La ficha completa funciona | 1 | Cumplido, salvo adjuntos locales |
| 5 | Los datos se guardan en Room | 0 y 1 | Cumplido |
| 6 | La información persiste tras cerrar la app | 0 y 1 | Cumplido |
| 7 | La bandeja permite procesar capturas | 1 | Cumplido |
| 8 | Crear, modificar y completar tareas | 2 | Cumplido |
| 9 | El motor de prioridad funciona | 2 | Cumplido |
| 10 | El dashboard refleja información real | 3 | Cumplido |
| 11 | Los filtros y la búsqueda funcionan | 2 | Cumplido |
| 12 | Los proyectos permiten relacionar tareas | 4 | Cumplido |
| 13 | Las revisiones pueden guardarse | 5 | Pendiente |
| 14 | Los recordatorios funcionan | Tras la 3 | Cumplido dentro de las restricciones de Android |
| 15 | Exportar y restaurar los datos | 6 | Pendiente |
| 16 | Existen pruebas automatizadas relevantes | Continuo | 247 en computador y 14 en dispositivo |
| 17 | No hay errores críticos conocidos | Continuo | Ninguno conocido al cerrar la Fase 4 |
| 18 | La aplicación funciona sin conexión | Continuo | La APK no declara permiso de internet |

## Anexo: prompt maestro literal

Copia exacta de la especificación entregada el 2026-11-08.

---

EXECUTIVE OS
PROMPT MAESTRO DE DESARROLLO — ANDROID
Actúa como un equipo multidisciplinario de desarrollo de software compuesto por:

1. Arquitecto de software senior especializado en Android.
2. Desarrollador senior Kotlin y Jetpack Compose.
3. Especialista en Clean Architecture, MVVM y diseño de bases de datos.
4. Diseñador UX/UI especializado en productividad y aplicaciones ejecutivas.
5. Ingeniero QA especializado en pruebas automatizadas.
6. Especialista en seguridad, privacidad y almacenamiento local.
7. Product Manager especializado en herramientas de gestión ejecutiva.

Tu responsabilidad es diseñar, desarrollar, probar y documentar una aplicación Android llamada EXECUTIVE OS.
No debes limitarte a generar ejemplos de código o pantallas visuales. Debes construir una aplicación real, funcional, mantenible, segura y preparada para evolucionar.
Debes trabajar sobre el proyecto Android existente, inspeccionando primero su estructura y estado antes de modificarlo.
1. VISIÓN DEL PRODUCTO
1.1. Nombre
EXECUTIVE OS.
1.2. Concepto
EXECUTIVE OS será un sistema personal de gestión ejecutiva que permita organizar de manera integral las responsabilidades laborales, académicas, profesionales, proyectos y asuntos personales de una persona con múltiples compromisos simultáneos.
No debe ser simplemente una aplicación de lista de tareas.
Debe funcionar como un sistema personal de dirección y ejecución que ayude a:

* Capturar información y compromisos.
* Organizar responsabilidades.
* Identificar prioridades reales.
* Entender la importancia y las consecuencias de cada tarea.
* Transformar tareas ambiguas en acciones concretas.
* Administrar proyectos.
* Planificar el tiempo.
* Identificar atrasos y riesgos.
* Gestionar objetivos de aprendizaje.
* Evaluar el desempeño personal.
* Reducir la sobrecarga mental.
* Mejorar la ejecución y la concentración.

La aplicación debe ayudar al usuario a responder diariamente:

1. ¿Qué tengo pendiente?
2. ¿Qué es realmente importante?
3. ¿Qué debo hacer primero?
4. ¿Qué puede esperar?
5. ¿Qué está en riesgo de retrasarse?
6. ¿Qué está bloqueado?
7. ¿Qué debo hacer concretamente para avanzar?
8. ¿Estoy distribuyendo bien mi tiempo?
9. ¿Estoy avanzando en mis objetivos profesionales y personales?

1.3. Filosofía de diseño
La aplicación debe seguir estos principios:

* Claridad antes que complejidad.
* Ejecución antes que administración.
* Captura rápida antes que clasificación perfecta.
* Prioridades fundamentadas antes que listas interminables.
* Información contextual antes que títulos aislados.
* Automatización controlada antes que decisiones automáticas opacas.
* Simplicidad visual antes que saturación de indicadores.
* Persistencia y confiabilidad antes que funcionalidades decorativas.

La aplicación no debe convertirse en otra fuente de trabajo para el usuario.
Registrar y organizar las tareas debe ser sencillo y rápido.
2. DECISIONES TÉCNICAS Y RESTRICCIONES
2.1. Plataforma
Desarrollar exclusivamente para Android en la primera etapa.
Compatibilidad objetivo:

* Android 10 o superior.
* Kotlin.
* Jetpack Compose.
* Material Design 3.

2.2. Herramientas
Utilizar:

* Android Studio.
* Claude Code.
* Git.
* Gradle Kotlin DSL.

2.3. Arquitectura
Utilizar una arquitectura MVVM con Clean Architecture pragmática.
Separar:

* Presentación.
* Dominio.
* Datos.
* Persistencia.
* Servicios de infraestructura.

No introducir capas innecesarias ni abstracciones que no aporten valor.
2.4. Tecnologías
Stack obligatorio, utilizando versiones estables y compatibles entre sí:

* Kotlin.
* Jetpack Compose.
* Material 3.
* Navigation Compose.
* Room.
* Hilt.
* Kotlin Coroutines.
* Kotlin Flow.
* DataStore.
* WorkManager.
* JUnit.
* MockK o alternativa compatible.
* Compose UI Testing.
* java.time.

Utilizar Version Catalog de Gradle cuando sea conveniente.
2.5. Funcionamiento local
La primera versión debe ser offline-first.
La base de datos local será la fuente de verdad.
La aplicación deberá funcionar sin conexión para:

* Crear tareas.
* Consultar tareas.
* Modificar tareas.
* Eliminar o archivar tareas.
* Gestionar proyectos.
* Registrar sesiones de estudio.
* Consultar el dashboard.
* Realizar revisiones.
* Consultar el historial.

No depender de servicios web para las operaciones principales.
2.6. Integraciones futuras
Dejar preparada la arquitectura para incorporar posteriormente:

* Gmail.
* Google Calendar.
* Microsoft Outlook.
* Google Tasks.
* Sincronización multidispositivo.
* Copias de seguridad en la nube.
* Inteligencia artificial.

Estas funcionalidades no deben implementarse en el MVP salvo que se solicite expresamente.
La futura integración de Gmail debe realizarse mediante OAuth 2.0 y API oficial, con permisos mínimos y consentimiento explícito.
No almacenar contraseñas de cuentas externas.
3. DISEÑO UX/UI
3.1. Identidad visual
Crear una interfaz profesional, moderna, sobria y ejecutiva.
Debe transmitir:

* Control.
* Claridad.
* Profesionalismo.
* Organización.
* Concentración.
* Confianza.

No utilizar una estética infantil, excesivamente colorida o de videojuego.
Utilizar Material Design 3 con una personalización visual propia.
3.2. Diseño adaptable
La aplicación debe funcionar correctamente en diferentes tamaños de pantalla Android.
Implementar:

* Tema claro.
* Tema oscuro.
* Tipografía legible.
* Jerarquía visual clara.
* Espaciado consistente.
* Componentes reutilizables.
* Estados vacíos.
* Indicadores de carga.
* Mensajes de error comprensibles.

3.3. Navegación principal
La navegación debe incluir:

1. Inicio.
2. Bandeja.
3. Tareas.
4. Proyectos.
5. Planificación.
6. Formación.
7. Revisiones.
8. Configuración.

Utilizar navegación inferior para las secciones principales y navegación secundaria cuando sea necesario.
No saturar la pantalla con demasiadas opciones simultáneas.
3.4. Captura global
Implementar un botón flotante permanente:

* CAPTURAR

Debe estar disponible desde las principales pantallas.
Debe abrir la captura rápida de tareas.
La captura debe requerir el menor número posible de interacciones.
4. MÓDULO CENTRAL: CAPTURA INTELIGENTE DE TAREAS
ESTE MÓDULO ES UNA DE LAS FUNCIONALIDADES MÁS IMPORTANTES DEL PRODUCTO.
No implementar una captura limitada a un título.
Cada tarea debe poder conservar suficiente información para que el usuario recuerde qué debe hacer, por qué es importante, cuál es el resultado esperado y qué consecuencias puede tener no realizarla.
Al mismo tiempo, registrar una tarea no debe convertirse en completar un formulario extenso.
La solución obligatoria será una arquitectura de captura progresiva.
4.1. Dos modalidades
Implementar dos modalidades:
A. Captura rápida.
B. Ficha completa.
La captura rápida debe permitir registrar una tarea en aproximadamente 15 a 30 segundos.
La ficha completa debe permitir documentar una responsabilidad compleja en aproximadamente uno o dos minutos.
Estos tiempos son objetivos de diseño que deben verificarse mediante pruebas de usabilidad, no garantías.
4.2. Captura rápida
La pantalla debe contener:
Campo 1. Título o descripción breve.
Obligatorio.
Ejemplos:

* Preparar informe de riesgos.
* Revisar propuesta del proveedor.
* Estudiar modelos de riesgo de crédito.
* Enviar información al comité.
* Revisar indicadores operacionales.
* Preparar presentación ejecutiva.

Campo 2. Categoría.
Opciones:

* Trabajo.
* Estudio.
* Proyecto.
* Personal.
* Finanzas.
* Salud.
* Otro.

Campo 3. Fecha.
Opciones:

* Hoy.
* Mañana.
* Esta semana.
* Elegir fecha.
* Sin fecha definida.

La fecha elegida aquí debe interpretarse como fecha de vencimiento solo cuando el usuario lo confirme. No asumir que una fecha objetivo equivale a una obligación formal.
Campo 4. Detalles opcionales.
Permitir ampliar la ficha para completar contexto.
Botón principal:
GUARDAR CAPTURA.
4.3. Reglas de captura rápida

* El título es el único campo obligatorio.
* Guardar debe ser inmediato.
* No exigir prioridad para guardar.
* No exigir proyecto para guardar.
* No exigir fecha para guardar.
* No exigir descripción.
* Autoguardar borradores.
* Conservar contenido si se cierra accidentalmente la aplicación.
* Evitar duplicar capturas por doble pulsación.
* Mostrar confirmación discreta.
* Después de guardar, permitir capturar otra tarea inmediatamente.

El usuario debe poder capturar una idea mientras está en una reunión sin interrumpir su actividad.
4.4. Ficha completa de tarea
Diseñar una ficha dividida en secciones.
SECCIÓN A. IDENTIFICACIÓN
Campos:

* Título.
* Descripción.
* Categoría.
* Proyecto relacionado.
* Etiquetas.

SECCIÓN B. CONTEXTO
Campos:

* ¿Qué necesito hacer?
* ¿Por qué necesito hacerlo?
* ¿Quién solicitó esta tarea?
* ¿De dónde proviene?
* ¿Qué contexto debo recordar?
* ¿Qué información o documentos necesito?

Permitir notas y enlaces.
SECCIÓN C. IMPORTANCIA
Campo:
¿Por qué es importante esta tarea?
Permitir explicar:

* Impacto esperado.
* Consecuencia de no realizarla.
* Consecuencia de realizarla tarde.
* Personas o áreas afectadas.
* Riesgo relacionado.

No obligar a completar todos los campos.
SECCIÓN D. RESULTADO ESPERADO
Campo:
¿Cómo sabré que esta tarea está terminada?
Ejemplos:

* Informe revisado y enviado.
* Presentación aprobada.
* Análisis documentado.
* Indicadores actualizados.
* Certificación completada.

Este campo debe ayudar a evitar tareas ambiguas.
SECCIÓN E. SIGUIENTE ACCIÓN
Campo:
¿Cuál es el siguiente paso concreto?
Ejemplos:

* Descargar información.
* Revisar el archivo.
* Solicitar datos.
* Agendar reunión.
* Preparar borrador.

Debe ser un campo destacado.
Si una tarea es demasiado grande, sugerir dividirla en subtareas.
SECCIÓN F. PRIORIZACIÓN
Campos:
Impacto: escala de 1 a 5.
Urgencia: escala de 1 a 5.
Riesgo de retraso: escala de 1 a 5.
Agregar explicaciones breves para cada escala.
Impacto:
1 = insignificante.
2 = bajo.
3 = moderado.
4 = alto.
5 = crítico.
Urgencia:
1 = puede esperar.
2 = no requiere atención inmediata.
3 = requiere planificación próxima.
4 = requiere atención prioritaria.
5 = requiere actuación inmediata.
Riesgo de retraso:
1 = consecuencias mínimas.
2 = consecuencias bajas.
3 = consecuencias moderadas.
4 = consecuencias importantes.
5 = consecuencias críticas.
SECCIÓN G. FECHAS Y TIEMPO
Campos:

* Fecha de creación.
* Fecha de inicio.
* Fecha objetivo.
* Fecha límite.
* Duración estimada.
* Duración real.
* Recordatorio.

Distinguir fecha objetivo y fecha límite.
No inventar fechas.
SECCIÓN H. RESPONSABLES
Campos:

* Responsable principal.
* Personas involucradas.
* Dependencias.
* Estado de delegación.

SECCIÓN I. DOCUMENTACIÓN
Permitir:

* Notas.
* Enlaces.
* Referencias.
* Adjuntos locales cuando sean compatibles.

4.5. Captura progresiva
No mostrar todos los campos al mismo tiempo.
Diseñar:

* Vista rápida inicial.
* Botón "Añadir contexto".
* Botón "Evaluar importancia".
* Botón "Definir siguiente acción".
* Botón "Más opciones".

Permitir completar posteriormente la información.
No obligar a completar la ficha completa para crear una tarea.
4.6. Inteligencia de captura
Implementar funciones sin necesidad de inteligencia artificial:

* Recordar última categoría seleccionada.
* Recordar último proyecto seleccionado.
* Sugerir plantillas.
* Detectar campos vacíos.
* Mostrar tareas similares existentes.
* Identificar posibles duplicados mediante coincidencia de texto.
* Permitir convertir una tarea en proyecto.
* Permitir convertir una captura en nota.
* Permitir delegar una tarea.

Las sugerencias nunca deben modificar datos sin confirmación.
4.7. Plantillas de captura
Crear plantillas iniciales:

1. Preparar informe.
2. Revisar documento.
3. Responder solicitud.
4. Preparar reunión.
5. Realizar análisis.
6. Estudiar tema.
7. Completar formación.
8. Hacer seguimiento.
9. Resolver incidencia.
10. Actividad personal.

Las plantillas deben ser editables.
4.8. Bandeja de entrada
Toda captura rápida debe entrar inicialmente en una bandeja de entrada.
La bandeja debe permitir:

* Abrir.
* Editar.
* Completar contexto.
* Convertir en tarea.
* Convertir en proyecto.
* Convertir en nota.
* Delegar.
* Archivar.
* Eliminar.
* Posponer su procesamiento.

Implementar procesamiento por lotes.
No perder el contenido original al convertir una captura.
5. MÓDULO DE TAREAS
5.1. Funcionalidades
Implementar:

* Crear.
* Consultar.
* Editar.
* Completar.
* Cancelar.
* Archivar.
* Duplicar.
* Delegar.
* Posponer.
* Dividir en subtareas.
* Crear dependencias.
* Añadir notas.
* Consultar historial.

5.2. Estados
Utilizar los siguientes estados:

* INBOX.
* PENDING.
* IN_PROGRESS.
* WAITING.
* BLOCKED.
* COMPLETED.
* CANCELLED.
* ARCHIVED.

5.3. Vistas
Crear:

* Todas las tareas.
* Hoy.
* Próximas.
* Vencidas.
* Prioridad crítica.
* Prioridad alta.
* En progreso.
* Esperando respuesta.
* Bloqueadas.
* Completadas.
* Sin fecha.

5.4. Filtros
Permitir filtrar por:

* Categoría.
* Proyecto.
* Prioridad.
* Fecha.
* Estado.
* Responsable.
* Tiempo estimado.
* Etiquetas.

5.5. Búsqueda
Implementar búsqueda por:

* Título.
* Descripción.
* Contexto.
* Proyecto.
* Etiquetas.

La búsqueda debe ser rápida y tolerar diferencias razonables de mayúsculas y minúsculas.
6. MOTOR DE PRIORIZACIÓN
Implementar un motor transparente.
Fórmula inicial:
P = (Impacto × 0.40) + (Urgencia × 0.25) + (Riesgo de retraso × 0.35)
Escala de 1 a 5.
Clasificación:
4.00 a 5.00: CRÍTICA.
3.00 a 3.99: ALTA.
2.00 a 2.99: MEDIA.
1.00 a 1.99: BAJA.
6.1. Reglas

* Los pesos deben ser configurables.
* Los umbrales deben ser configurables.
* Mostrar cómo se obtuvo la puntuación.
* Permitir prioridad manual justificada.
* No cambiar prioridades silenciosamente.
* No asumir valores para campos vacíos.
* Si faltan datos, mostrar prioridad pendiente o provisional.

6.2. Fechas
Considerar:

* Tareas vencidas.
* Fechas próximas.
* Compromisos futuros.
* Tareas sin fecha.
* Dependencias pendientes.

No confundir una fecha próxima con importancia estratégica.
6.3. Obligaciones críticas
Permitir marcar tareas como:

* Regulatorias.
* Legales.
* Auditoría.
* Riesgo material.
* Compromiso contractual.
* Compromiso directivo.

Estas categorías deben activar una advertencia o revisión especial.
No permitir que la puntuación matemática oculte una obligación crítica.
6.4. Explicación
Cada prioridad debe mostrar una explicación comprensible.
Ejemplo:
"Prioridad alta porque tiene impacto elevado, fecha próxima y consecuencias importantes en caso de retraso."
7. DASHBOARD EJECUTIVO
Diseñar una pantalla principal que permita comprender rápidamente la situación general.
7.1. Indicadores
Mostrar:

* Tareas pendientes.
* Tareas vencidas.
* Tareas críticas.
* Tareas de alta prioridad.
* Tareas en progreso.
* Tareas bloqueadas.
* Tareas completadas durante la semana.
* Tiempo planificado.
* Tiempo disponible estimado.

7.2. Tres prioridades principales
Mostrar tres prioridades recomendadas para el día.
Cada tarjeta debe incluir:

* Título.
* Proyecto.
* Prioridad.
* Fecha límite.
* Tiempo estimado.
* Siguiente acción.
* Motivo de la recomendación.

El usuario debe poder modificar esta selección.
No imponer exactamente tres tareas si el usuario tiene menos o si una emergencia requiere atención adicional.
7.3. Alertas ejecutivas
Mostrar:

* Compromisos vencidos.
* Compromisos próximos.
* Tareas bloqueadas.
* Dependencias pendientes.
* Sobrecarga de calendario.
* Proyectos con retrasos.
* Tareas críticas sin siguiente acción.

No generar alertas innecesarias.
7.4. Próximas 24 horas
Mostrar compromisos próximos y actividades planificadas.
8. MÓDULO DE PROYECTOS
Permitir gestionar proyectos con:

* Nombre.
* Descripción.
* Objetivo.
* Justificación.
* Responsable.
* Fecha de inicio.
* Fecha objetivo.
* Estado.
* Prioridad.
* Entregables.
* Hitos.
* Riesgos.
* Tareas asociadas.
* Notas.

8.1. Estados

* PLANNED.
* ACTIVE.
* ON_HOLD.
* AT_RISK.
* COMPLETED.
* CANCELLED.

8.2. Seguimiento
Mostrar:

* Progreso.
* Hitos completados.
* Próximo hito.
* Tareas pendientes.
* Riesgos.
* Desviaciones.
* Fecha prevista de finalización.

El progreso no debe calcularse únicamente contando tareas pequeñas.
Utilizar hitos y entregables ponderados.
9. PLANIFICACIÓN Y GESTIÓN DEL TIEMPO
Crear vistas:

* Diaria.
* Semanal.
* Mensual.

Permitir asignar tareas a bloques de tiempo.
9.1. Tipos de bloques
A. Trabajo profundo.
B. Coordinación y reuniones.
C. Estudio y desarrollo profesional.
D. Actividades personales y recuperación.
9.2. Funcionalidades

* Crear bloques.
* Editar bloques.
* Mover bloques.
* Relacionar bloques con tareas.
* Detectar solapamientos.
* Consultar disponibilidad.
* Registrar tiempo real.
* Comparar planificación y ejecución.

9.3. Capacidad
Usar inicialmente un objetivo de planificación de aproximadamente 75% de la capacidad disponible.
Dejar margen para imprevistos.
No considerar todo el tiempo libre como capacidad productiva.
No sobrecargar automáticamente el calendario.
10. MÓDULO DE FORMACIÓN Y DESARROLLO PROFESIONAL
Crear un módulo para organizar la formación profesional.
Permitir registrar:

* Objetivos de aprendizaje.
* Temas.
* Cursos.
* Materiales.
* Sesiones.
* Tiempo dedicado.
* Evaluaciones.
* Nivel de dominio.
* Evidencias.
* Fecha objetivo.

Incluir categorías iniciales:

* Riesgo de crédito.
* Riesgo de mercado.
* Riesgo operacional.
* Riesgo de liquidez.
* Riesgo de modelo.
* Riesgo de tasa de interés.
* Gobierno corporativo.
* Regulación.
* Gestión integral de riesgos.
* Liderazgo.
* Comunicación ejecutiva.
* Analítica y tecnología.

Permitir registrar horas de formación por semana y por tema.
11. REVISIONES DIARIAS Y SEMANALES
11.1. Revisión diaria
Crear una guía con preguntas:

1. ¿Qué tareas deben atenderse hoy?
2. ¿Cuáles son las tres prioridades?
3. ¿Qué compromisos vencen?
4. ¿Qué está bloqueado?
5. ¿Qué puedo delegar?
6. ¿Qué debo posponer conscientemente?
7. ¿Qué tiempo necesito proteger?

Permitir guardar las respuestas.
11.2. Cierre diario
Preguntar:

* ¿Qué terminé?
* ¿Qué quedó pendiente?
* ¿Por qué?
* ¿Qué cambió?
* ¿Qué debo preparar para mañana?

11.3. Revisión semanal
Incluir:

* Tareas completadas.
* Tareas pendientes.
* Tareas vencidas.
* Proyectos.
* Formación.
* Tiempo de concentración.
* Sobrecarga.
* Compromisos de la próxima semana.

Generar una vista resumida y recomendaciones basadas en los datos.
No presentar recomendaciones como hechos científicos si son simplemente heurísticas de la aplicación.
12. ANALÍTICA Y MÉTRICAS
Crear un módulo de indicadores.
Métricas:

* Porcentaje de tareas prioritarias completadas.
* Tareas vencidas.
* Tareas bloqueadas.
* Tiempo estimado frente a real.
* Horas de trabajo profundo registradas.
* Horas de formación.
* Cumplimiento semanal.
* Progreso de proyectos.
* Distribución del tiempo por categoría.

Definir claramente cada indicador.
No premiar artificialmente completar muchas tareas pequeñas.
No generar puntuaciones de productividad engañosas.
Permitir filtros por semana, mes, trimestre y categoría.
13. RECORDATORIOS Y NOTIFICACIONES
Implementar recordatorios locales.
Permitir:

* Recordatorios puntuales.
* Recordatorios de vencimiento.
* Avisos de tareas críticas.
* Recordatorios de revisión diaria.
* Recordatorios de revisión semanal.

Incluir:

* Configuración de horario silencioso.
* Posponer.
* Marcar como completada.
* Abrir tarea desde notificación.
* Evitar notificaciones duplicadas.

Utilizar WorkManager cuando sea apropiado.
Respetar las restricciones del sistema operativo Android para alarmas exactas y notificaciones.
No solicitar permisos innecesarios.
14. BASE DE DATOS
Utilizar Room.
Diseñar entidades independientes.
Entidades iniciales:

1. TaskEntity.
2. ProjectEntity.
3. InboxItemEntity.
4. CategoryEntity.
5. TagEntity.
6. TaskTagCrossRef.
7. TimeBlockEntity.
8. ReviewEntity.
9. LearningGoalEntity.
10. LearningSessionEntity.
11. ReminderEntity.
12. TaskDependencyEntity.
13. AuditLogEntity.
14. UserSettingsEntity.
15. ProjectMilestoneEntity.
16. TaskAttachmentEntity.

14.1. Reglas

* Usar UUID como identificador.
* Definir claves foráneas.
* Definir índices.
* Gestionar transacciones.
* Crear migraciones.
* Evitar pérdida de información.
* Mantener historial cuando sea relevante.
* No borrar información crítica accidentalmente.

14.2. Relaciones
Un proyecto puede tener muchas tareas.
Una tarea puede tener subtareas.
Una tarea puede depender de otras tareas.
Una tarea puede tener múltiples etiquetas.
Una captura puede convertirse en una tarea o proyecto.
Conservar referencia a la captura original.
14.3. Integridad
Impedir dependencias circulares.
Evitar tareas duplicadas por doble pulsación.
No permitir valores negativos de tiempo.
Validar fechas y estados.
15. SEGURIDAD Y PRIVACIDAD
La aplicación debe ser privada por diseño.
Requisitos:

* No enviar datos personales a servidores externos en el MVP.
* No incluir publicidad.
* No implementar seguimiento publicitario.
* No registrar información sensible en logs.
* No almacenar secretos en texto plano.
* Utilizar Android Keystore cuando sea necesario.
* Solicitar únicamente permisos imprescindibles.
* Proteger las exportaciones cuando contengan información sensible.
* Advertir sobre el almacenamiento de copias de seguridad.

Permitir exportar datos en CSV y JSON.
Permitir restaurar copias de seguridad.
La restauración debe incluir validaciones y confirmación antes de reemplazar datos.
No implementar cifrado casero. Utilizar mecanismos criptográficos establecidos y bibliotecas mantenidas cuando sean necesarios.
16. ESTRUCTURA DEL PROYECTO
Organizar el código de manera clara.
Estructura de referencia:
app/
src/
main/
java/com/executiveos/
ExecutiveOSApp.kt
MainActivity.kt

```
    core/
      common/
      database/
      designsystem/
      navigation/
      notifications/

    data/
      local/
        dao/
        entity/
      repository/

    domain/
      model/
      repository/
      usecase/
      priority/

    feature/
      dashboard/
      capture/
      inbox/
      tasks/
      projects/
      planning/
      review/
      learning/
      analytics/
      settings/

    di/

  res/

test/
androidTest/
```

docs/
PRODUCT_VISION.md
REQUIREMENTS.md
ARCHITECTURE.md
DATA_MODEL.md
CAPTURE_UX.md
ROADMAP.md
TEST_PLAN.md
DECISIONS.md
CLAUDE.md
README.md
Puedes ajustar la estructura cuando exista una justificación técnica, pero documenta la decisión.
17. CALIDAD Y PRUEBAS
No considerar terminada una funcionalidad porque compile visualmente.
Cada módulo debe tener pruebas apropiadas.
17.1. Pruebas unitarias
Probar:

* Cálculo de prioridades.
* Validaciones.
* Estados.
* Fechas.
* Conversión de capturas.
* Cálculo de progreso.
* Dependencias.
* Filtros.
* Métricas.

17.2. Pruebas de integración
Probar:

* Persistencia Room.
* Repositorios.
* Transacciones.
* Migraciones.
* Exportación.
* Restauración.
* Notificaciones.

17.3. Pruebas de interfaz
Probar:

* Crear tarea.
* Editar tarea.
* Captura rápida.
* Ficha completa.
* Procesar bandeja.
* Crear proyecto.
* Consultar dashboard.
* Marcar tarea completada.

17.4. Casos extremos
Probar:

* 500 tareas.
* 1.000 tareas.
* Sin conexión.
* Base de datos vacía.
* Almacenamiento insuficiente.
* Cierre inesperado.
* Migración de versión.
* Dependencias circulares.
* Fechas vencidas.
* Tareas sin fecha.
* Campos incompletos.
* Doble pulsación.
* Datos corruptos.
* Restauración de respaldo.

18. CRITERIOS DE ACEPTACIÓN DEL MVP
El MVP solo se considera funcional cuando:

1. La aplicación compila.
2. Se puede instalar en Android.
3. La captura rápida funciona.
4. La ficha completa funciona.
5. Los datos se guardan en Room.
6. La información persiste después de cerrar la app.
7. La bandeja permite procesar capturas.
8. Se pueden crear, modificar y completar tareas.
9. El motor de prioridad funciona y se puede probar.
10. El dashboard refleja información real.
11. Los filtros y la búsqueda funcionan.
12. Los proyectos permiten relacionar tareas.
13. Las revisiones pueden guardarse.
14. Los recordatorios funcionan dentro de las restricciones Android.
15. Se pueden exportar y restaurar los datos.
16. Existen pruebas automatizadas relevantes.
17. No hay errores críticos conocidos.
18. La aplicación funciona sin conexión.

19. FASES DE DESARROLLO
No construir toda la aplicación en una sola iteración.
Trabajar mediante fases.
FASE 0 — FUNDACIÓN

* Configuración Android.
* Arquitectura.
* Navegación.
* Tema.
* Room.
* Hilt.
* Pruebas básicas.

FASE 1 — CAPTURA E INBOX

* Captura rápida.
* Ficha completa.
* Bandeja de entrada.
* Procesamiento.
* Plantillas.
* Autoguardado.

FASE 2 — TAREAS Y PRIORIDAD

* CRUD.
* Estados.
* Fechas.
* Prioridad.
* Búsqueda.
* Filtros.
* Subtareas.
* Dependencias.

FASE 3 — DASHBOARD

* Indicadores.
* Tres prioridades.
* Alertas.
* Próximas 24 horas.

FASE 4 — PROYECTOS Y PLANIFICACIÓN

* Proyectos.
* Hitos.
* Bloques de tiempo.
* Planificación semanal.

FASE 5 — REVISIÓN Y FORMACIÓN

* Revisiones diarias.
* Revisiones semanales.
* Formación.
* Métricas.

FASE 6 — ESTABILIZACIÓN

* QA.
* Rendimiento.
* Seguridad.
* Copias de respaldo.
* APK.
* Documentación.

FASE FUTURA — INTEGRACIONES

* Gmail.
* Google Calendar.
* Outlook.
* Sincronización multidispositivo.
* Funcionalidades inteligentes.

20. METODOLOGÍA DE TRABAJO CON CLAUDE CODE
Esta sección es obligatoria.
No debes intentar desarrollar todas las fases de una sola vez.
Debes trabajar como un equipo de ingeniería profesional.
20.1. Antes de desarrollar
Primero:

1. Inspecciona el proyecto.
2. Comprueba archivos existentes.
3. Comprueba versiones de Gradle y Android.
4. Identifica dependencias.
5. Detecta errores.
6. Comprende la estructura actual.
7. Presenta un plan de implementación.

No sobrescribas archivos existentes sin inspeccionarlos.
20.2. Desarrollo incremental
Para cada módulo:

1. Explica el objetivo.
2. Define los archivos que modificarás.
3. Implementa una funcionalidad concreta.
4. Ejecuta compilación.
5. Ejecuta pruebas.
6. Corrige errores.
7. Documenta resultados.
8. Presenta resumen de cambios.

No continúes a otro módulo importante mientras existan errores críticos en el actual.
20.3. Calidad del código

* Código limpio.
* Nombres descriptivos.
* Funciones pequeñas.
* Responsabilidades separadas.
* Manejo correcto de errores.
* Validaciones.
* Pruebas.
* Documentación útil.
* Evitar duplicación.
* Evitar complejidad innecesaria.

No crear funciones vacías para aparentar que un módulo está terminado.
No utilizar datos ficticios en producción.
Los datos de demostración deben estar claramente identificados y ser opcionales.
20.4. Git
Realizar commits lógicos por funcionalidad.
No realizar cambios destructivos sin autorización.
No introducir secretos, claves o archivos personales en el repositorio.
20.5. Comunicación
Al terminar cada fase, informar:

* Qué se desarrolló.
* Qué archivos se modificaron.
* Qué decisiones se tomaron.
* Qué pruebas se ejecutaron.
* Resultado de compilación.
* Errores pendientes.
* Limitaciones.
* Próximo paso.

No afirmar que una función está implementada si únicamente existe su interfaz visual.
21. ENTREGABLES
Al completar el MVP entregar:

1. Proyecto Android completo.
2. Código fuente.
3. APK instalable.
4. Base de datos y esquema documentado.
5. Documentación de arquitectura.
6. Documentación funcional.
7. Manual básico de usuario.
8. Instrucciones de instalación.
9. Pruebas automatizadas.
10. Informe de pruebas.
11. Registro de decisiones técnicas.
12. Backlog de evolución.

22. INSTRUCCIÓN INICIAL: EMPEZAR POR SPRINT 0
Ahora comienza el trabajo.
No implementes todas las funcionalidades inmediatamente.
Primero inspecciona el entorno y el proyecto actual.
Después:

1. Resume el estado del proyecto.
2. Identifica qué está disponible.
3. Presenta la arquitectura propuesta.
4. Define las dependencias.
5. Identifica riesgos técnicos.
6. Prepara la estructura inicial.
7. Configura el proyecto Android.
8. Comprueba que compile.
9. Ejecuta las pruebas básicas.
10. Actualiza la documentación.

No implementes todavía los módulos completos de captura, tareas, proyectos o dashboard.
Detente cuando Sprint 0 esté completo y funcional.
Espera aprobación para comenzar Sprint 1.
PRINCIPIO FINAL
EXECUTIVE OS debe ser una herramienta que reduzca la carga mental y facilite la ejecución.
No debe exigir más tiempo del que ahorra.
La captura de tareas debe ser extraordinariamente sencilla, pero suficientemente completa para conservar contexto, importancia, consecuencias y siguiente acción.
La prioridad es construir un núcleo confiable y útil, no una aplicación enorme llena de funciones incompletas.
Construye con criterio de producto, calidad de ingeniería y orientación a uso real.
