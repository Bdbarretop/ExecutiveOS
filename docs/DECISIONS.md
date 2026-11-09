# Registro de decisiones

## Entorno

**D-001. Proyecto en `AndroidStudioProjects`, fuera de OneDrive.** La carpeta inicial contenía una «á» en la ruta, que AGP rechaza por defecto en Windows, y OneDrive bloquea archivos durante las compilaciones. *Decidido por el usuario el 2026-11-08.*

**D-002. Proyecto nuevo.** La carpeta indicada estaba vacía y el «proyecto existente» del brief no existía. FinanzasAPP es otro producto y no se reutiliza.

**D-003. Stack actual en lugar de congelarlo para Android Studio Narwhal.** Las librerías AndroidX actuales exigen AGP 9.2 o superior y compileSdk 37. Congelar el stack habría dejado el proyecto unos 16 meses atrás y con una migración pendiente. Coste: hay que actualizar el IDE a Rabbit 2 para abrir el proyecto. *Decidido por el usuario el 2026-11-08.*

## Compilación

**D-004. Kotlin 2.4.21 fijado de forma explícita.** AGP 9.5.1 trae Kotlin 2.2.10 por defecto. El alias `kotlin-android` con `apply false` en la raíz eleva la versión sin aplicar el plugin, que es incompatible con el DSL nuevo. Hilt 2.60.1 se compiló con Kotlin 2.3.21; la combinación se validó con una compilación real.

**D-005. compileSdk 37, targetSdk 36, minSdk 29.** core-ktx 1.19 exige compileSdk 37. El target 36 es el nivel más alto que se puede probar en el AVD disponible. minSdk 29 corresponde a Android 10, según el brief.

**D-006. Ajustes para 8 GB de RAM.** Heap de 2,5 GB, Kotlin dentro del proceso de Gradle y dos workers.

**D-007. WorkManager solo en el catálogo.** Añadirlo a `:app` sin usarlo incorporaría permisos innecesarios al manifiesto. Se integrará con el primer worker real.

## Arquitectura

**D-008. Un único módulo `:app`.** Con un solo desarrollador y este tamaño, varios módulos encarecen la compilación sin aportar aislamiento útil.

**D-009. Sin casos de uso que solo reenvían llamadas.** `domain/usecase` se creará cuando exista lógica que combine datos. Sigue la indicación del brief de no añadir capas sin valor.

**D-010. Límite entre DataStore y Room.** DataStore guarda preferencias propias del dispositivo. `UserSettingsEntity` guardará lo que deba viajar en las copias de seguridad.

**D-026. DataStore creado con el delegado `preferencesDataStore`.** Garantiza una instancia por proceso. Con una fábrica dentro del módulo de Hilt, cada prueba instrumentada creaba un segundo DataStore sobre el mismo archivo y la lectura fallaba.

**D-011. Room 2.8.5 en lugar de Room 3.** La línea 2.x es la de mayor madurez y documentación. El paso a Room 3 queda como evolución posible.

**D-012. El esquema v1 solo contiene `CategoryEntity`.** Sprint 0 debía demostrar la cadena, no diseñar todas las tablas. Las demás llegan con su módulo; así cada entidad se define con sus requisitos a la vista y las migraciones se ejercitan desde Sprint 1.

**D-013. Sin migración destructiva.** Una migración ausente debe fallar de forma visible. Perder datos en silencio es el peor resultado posible para esta app.

**D-014. Catálogo sembrado en `onCreate` con UUID fijos.** `onCreate` se ejecuta en transacción, y los UUID fijos mantienen las referencias al restaurar copias.

**D-027. Una captura crea siempre una tarea en estado `INBOX`.** Así existe una sola ficha, y los accesos «Añadir contexto» o «Evaluar importancia» guardan primero y completan después. El original se conserva aparte en `inbox_items`.

**D-028. `AutoMigration` para cambios aditivos.** Room verifica la migración al compilar y evita SQL escrito a mano. Se usa una migración manual cuando el cambio no sea aditivo.

**D-029. Tablas `notes` y `capture_templates`, no previstas en la sección 14.** Sin ellas no se podían cumplir «convertir en nota» ni las plantillas editables. *Propuesta aceptada por el usuario al aprobar Sprint 1.*

**D-030. Proyecto mínimo en Sprint 1.** «Convertir en proyecto» exige una tabla de destino. Contiene solo lo que la conversión puede rellenar; la Fase 4 la amplía.

**D-031. Borradores de captura en DataStore.** Son estado del dispositivo y no deben viajar en las copias de seguridad.

**D-032. La ficha se guarda sola, sin botón.** Cada edición válida se persiste tras una pausa breve; así no hay cambios que perder. Si los datos no son válidos, la ficha avisa y no guarda.

**D-033. «Esta semana» es el domingo de la semana en curso, y se guarda como fecha objetivo.** Solo es vencimiento si el usuario lo confirma, como exige la sección 4.2.

**D-034. Campo de recordatorio y adjuntos pospuestos.** La ficha no muestra «Recordatorio» porque aún no hay recordatorios que lo atiendan, ni adjuntos locales, que requieren permisos de archivos. Enlaces, notas y referencias sí están disponibles. Mostrar campos sin función contradiría la sección 20.3.

**D-036. Motor de prioridad con aritmética entera.** Pesos en porcentaje y umbrales en centésimas. Con decimales de punto flotante, un 4,00 exacto podría clasificarse como 3,99.

**D-037. Las fechas y las dependencias no alteran la puntuación.** Explican y avisan, pero el brief prohíbe confundir una fecha próxima con importancia estratégica (6.2).

**D-038. Sin las tres escalas no hay puntuación.** El brief prohíbe asumir valores (6.1), así que se muestra «Sin evaluar» e indica qué falta, en lugar de calcular con datos parciales.

**D-039. Las obligaciones críticas aparecen en la vista Críticas y primero en cualquier lista.** No modifican la puntuación, pero ninguna puntuación las oculta (6.3).

**D-040. Cambiar pesos o umbrales pide confirmación si alguna tarea cambia de nivel.** Es la forma de cumplir «no cambiar prioridades silenciosamente» cuando el cambio es global.

**D-041. Las vistas y filtros se resuelven en memoria.** Evita decenas de consultas específicas. Con 1.000 tareas, la selección tardó 68 ms en la JVM del computador; el rendimiento en teléfono se medirá en la Fase 6.

**D-042. Paquete `feature/tasks`, en plural.** Se renombró para coincidir con la estructura de la sección 16.

**D-043. El reloj usa la zona del dispositivo.** Con `Clock.systemUTC()`, `LocalDate.now(clock)` devolvía el día siguiente durante la noche en América. Los instantes no cambian; las fechas de «Hoy» guardadas de noche antes de esta corrección pueden tener un día de más.

**D-044. Las prioridades del día ordenan por emergencia, nivel y fecha, en ese orden.** La fecha solo desempata, según 6.2. Una emergencia es una obligación con fecha vencida o en 7 días, o una tarea crítica vencida o de hoy, y se añade aunque supere las tres (7.2).

**D-045. Lo bloqueado no se recomienda, salvo las obligaciones.** Una tarea bloqueada, en espera o con dependencias abiertas no se puede avanzar hoy, pero una obligación crítica no debe ocultarse.

**D-046. La selección del día vive en DataStore y caduca al cambiar de fecha.** Es un ajuste diario del dispositivo y no debe viajar en las copias de seguridad.

**D-047. Solo la fecha límite o una obligación cuentan como compromiso en las alertas.** Una fecha objetivo incumplida no es un compromiso vencido (4.2); sigue apareciendo en la vista Vencidas.

**D-048. Sin valores simulados por falta de Planificación.** El tiempo disponible indica que llega en la Fase 4, y la sobrecarga y los retrasos de proyectos no generan alertas hasta entonces.

**D-049. WorkManager sin alarmas exactas.** El permiso de alarmas exactas no se justifica en una app de tareas, y la sección 13 pide respetar esas restricciones. A cambio, los avisos pueden retrasarse unos minutos, y la interfaz lo indica.

**D-050. Un único aviso diario de compromisos.** Varias fechas límite y tareas críticas se resumen en una notificación, para no generar alertas innecesarias.

**D-051. Permisos añadidos.** `POST_NOTIFICATIONS` se pide solo al crear o activar un aviso. WorkManager añade `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_NETWORK_STATE` y `FOREGROUND_SERVICE`, que no requieren aprobación del usuario y no dan acceso a internet.

**D-052. Los recordatorios de revisión abren Inicio.** La pantalla de Revisiones llega en la Fase 5; están desactivados por defecto.

**D-053. El recordatorio de la ficha ya existe.** Resuelve la parte de recordatorios de D-034; los adjuntos locales siguen pendientes.

## Proyectos y planificación

**D-054. La disponibilidad se consulta por capacidad, no por franjas.** El brief no define un horario laboral, y sin él cualquier hueco sería inventado. Se muestra cuánto margen queda hasta el objetivo de cada periodo. Las franjas quedan en el backlog.

**D-055. Los solapamientos avisan, pero no impiden guardar.** A veces se aceptan dos compromisos a la vez a propósito; la decisión es de la persona. Lo mismo ocurre al superar el objetivo diario: nunca se reorganiza nada de forma automática.

**D-056. Un bloque pertenece al día en que empieza.** El editor exige que termine el mismo día. Si al moverlo cruza la medianoche, cuenta entero para su día de inicio.

**D-057. Un día no laborable no se sobrecarga.** No tiene capacidad configurada, así que planificar en él no genera alerta.

**D-058. El tiempo disponible de Inicio va de hoy al domingo.** Los días ya pasados no aportan tiempo; contarlos daría un margen que ya no existe.

**D-059. El peso inicial de un hito es 1.** Es visible y editable antes de guardar, y equivale a repartir el avance por igual hasta que la persona indique otra cosa.

**D-060. Plan y ejecución comparan solo bloques con tiempo real.** Un bloque sin registrar no es una desviación; compararlo daría diferencias falsas.

**D-061. Los ajustes de una sola fila usan `@Upsert`.** Sustituye a `REPLACE`, que las reglas del proyecto prohíben sobre datos del usuario. No cambia el esquema. D-048 queda resuelta con esta fase.

## Revisiones, formación y métricas

**D-062. Los recordatorios de revisión abren su guía.** Sustituye a D-052, ahora que la pantalla existe.

**D-063. Las respuestas de una revisión se guardan juntas en JSON.** Se escriben de una vez con el autoguardado, y una pregunta retirada en el futuro no rompe las revisiones antiguas.

**D-064. Hay una revisión por tipo y periodo.** La semanal se identifica por su lunes, de modo que abrirla cualquier día de la semana edita la misma. Abrir una revisión y salir sin escribir no la crea.

**D-065. Los temas de formación son texto libre.** Se ofrecen las 12 categorías iniciales del brief como sugerencias, y escribir otra ya la amplía, sin necesidad de un catálogo aparte.

**D-066. Las sugerencias semanales solo aparecen con datos que las respalden.** Se evitan umbrales inventados: por ejemplo, se sugiere proteger trabajo profundo solo si no hay ninguno registrado. La pantalla indica que son reglas de la app y no conclusiones demostradas.

**D-067. Las métricas no tienen puntuación global.** Cada indicador muestra sus dos términos y su definición. El cumplimiento excluye subtareas y prioridad baja para no premiar trocear el trabajo, y sin datos se dice «Sin datos», nunca 0 %.

**D-068. Métricas es una entrada más en «Más».** El brief pide el módulo pero no le asigna sitio en la navegación; va antes de Configuración y respeta el orden de las demás secciones.

**D-069. La distribución del tiempo usa las tareas completadas con tiempo real.** Es el único dato que tiene categoría; los bloques de tiempo no la tienen, y asignársela por la tarea vinculada dejaría fuera a los que no tienen tarea.

**D-070. El resumen semanal de semanas pasadas muestra pendientes y vencidas de hoy.** No hay un histórico diario de esos valores; la pantalla lo indica.

## Copias, seguridad y entrega

**D-071. Las copias se cifran con algoritmos estándar de la plataforma.** AES-256-GCM con clave derivada por PBKDF2-HMAC-SHA256 y 600.000 iteraciones, mediante las API criptográficas de Android, sin algoritmos propios. Se descarta Android Keystore para este fin porque una clave ligada al dispositivo impediría restaurar en otro teléfono.

**D-072. La contraseña de una copia no se guarda.** Tampoco pasa al estado restaurable de la pantalla. La contrapartida, advertida en la app, es que olvidarla hace irrecuperable la copia.

**D-073. Una copia se ensaya antes de aplicarla.** Se carga en una base temporal en memoria y se lee con los repositorios reales. Así, un estado desconocido o una fecha rota se detectan sin mantener una lista de reglas por columna que quedaría desactualizada.

**D-074. Restaurar sustituye todo en una sola transacción.** Combinar datos de dos orígenes crearía conflictos sin una respuesta correcta. Con reemplazo atómico, cualquier fallo deja los datos intactos.

**D-075. Se aceptan copias de esquemas anteriores y se rechazan las más recientes.** Las migraciones han sido aditivas, de modo que una copia antigua sigue siendo válida. **Una futura migración no aditiva deberá transformar también las copias antiguas.**

**D-076. El CSV es solo para consultar.** Va un archivo por tabla en un ZIP. Los textos que una hoja de cálculo ejecutaría como fórmula se neutralizan.

**D-077. Los archivos llegan por el selector del sistema.** La app no pide permisos de almacenamiento.

**D-078. La base no se cifra en el dispositivo.** Reside en el almacenamiento privado de la app, con copias automáticas de Android desactivadas. La app no guarda secretos, por lo que Android Keystore no es necesario hoy. El cifrado en reposo queda en el backlog.

**D-079. La APK se firma con la clave de depuración local mientras no exista una propia.** Permite actualizar la instalación actual sin desinstalar, lo que borraría los datos, y evita crear secretos que el usuario deba custodiar sin haberlo decidido. Sirve para uso personal, no para distribuirla. El archivo opcional `keystore.properties`, fuera de Git, activa una clave propia. **Cambiar de clave exige desinstalar: antes hay que hacer una copia.**

**D-080. Los datos recibidos por la actividad pública se validan.** `MainActivity` admite intents de otras apps; un tipo de revisión desconocido se ignora en lugar de provocar un cierre.

## Interfaz

**D-015. Navegación: cuatro secciones y «Más».** Material recomienda un máximo de cinco destinos en la barra inferior. Se respeta el orden del brief: las cuatro primeras secciones van en la barra y las otras cuatro en «Más». Es fácil de ajustar si el uso real indica otra cosa.

**D-016. Navigation Compose 2.x con rutas tipadas.** El brief exige Navigation Compose; Navigation 3 es otra biblioteca con otro modelo.

**D-017. Sin color dinámico.** La identidad visual no debe depender del fondo de pantalla del usuario.

**D-018. Tipografía del sistema.** Evita incluir fuentes en la APK y las fuentes descargables, que requerirían red.

**D-019. Iconos como vectores locales.** Se usan trazados de Material Icons (Apache 2.0) en `res/drawable`, sin añadir la biblioteca completa.

**D-020. Secciones pendientes declaradas como tales.** Un único `SectionPlaceholder` indica en qué fase llega cada sección. No hay pantallas simuladas ni datos de demostración.

## Seguridad

**D-021. Sin permiso `INTERNET` en el MVP.** Convierte «no enviar datos a servidores» en una garantía verificable sobre la APK.

**D-022. Copias automáticas de Android desactivadas.** Sin `allowBackup` ni extracción, los datos personales no llegan a la nube sin una acción explícita del usuario.

## Proceso

**D-023. Pruebas de interfaz en JVM con Robolectric.** Con 8 GB de RAM, el emulador no es viable de forma habitual junto a Gradle. Robolectric permite ejecutar el arranque real con Hilt, Room y DataStore sin dispositivo. Se mantiene una prueba instrumentada para dispositivo real.

**D-024. Una rama por sprint.** Cada sprint vive en su rama; la fusión a `main` queda sujeta a aprobación. El código se aloja en el repositorio privado `Bdbarretop/ExecutiveOS`.

**D-035. Base en memoria para las pruebas en dispositivo.** Se ejecutan en el teléfono real del usuario y no deben dejar datos de prueba en su app.

**D-025. Ajustes a la estructura de referencia del brief.** Se añade `feature/more` para el menú secundario. Los paquetes `core/common`, `core/notifications`, `domain/usecase`, `domain/priority` y los demás `feature/*` se crearán cuando tengan código.
