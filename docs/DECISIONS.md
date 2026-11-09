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

**D-024. Una rama por sprint.** Sprint 0 vive en `sprint-0/fundacion`; la fusión a `main` queda sujeta a aprobación.

**D-025. Ajustes a la estructura de referencia del brief.** Se añade `feature/more` para el menú secundario. Los paquetes `core/common`, `core/notifications`, `domain/usecase`, `domain/priority` y los demás `feature/*` se crearán cuando tengan código.
