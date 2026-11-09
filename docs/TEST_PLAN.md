# Plan de pruebas

Una funcionalidad no se considera terminada porque compile: debe tener pruebas apropiadas y funcionar de extremo a extremo.

## Niveles

| Nivel | Ubicación | Herramientas | Requiere dispositivo |
|---|---|---|---|
| Unitarias JVM | `app/src/test` | JUnit, MockK, coroutines-test | No |
| Integración y UI en JVM | `app/src/test` | Robolectric, Room, Hilt, Compose UI Test | No |
| Instrumentadas | `app/src/androidTest` | AndroidJUnitRunner, Hilt, Compose UI Test, `MigrationTestHelper` | Sí |

Se prefiere Robolectric para la mayoría de las pruebas de integración porque el equipo de desarrollo tiene 8 GB de RAM. Lo que depende del dispositivo real se comprueba con pruebas instrumentadas.

## Cómo ejecutar

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

En dispositivo:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

### Preparación de un Xiaomi con HyperOS

HyperOS añade restricciones que afectan a las pruebas, no a la app:

1. En Opciones de desarrollador, activar **Depuración USB** e **Instalar vía USB**. Esta última exige haber iniciado sesión en la cuenta Mi.
2. En la información de la app, dentro de **Otros permisos**, permitir **Abrir nuevas ventanas mientras se ejecuta en segundo plano**. Sin este permiso cada prueba queda bloqueada al abrir la actividad; en el registro aparece `MIUILOG- Permission Denied Activity`.
3. Mantener el teléfono desbloqueado durante la ejecución.
4. Para que los recordatorios lleguen con la app cerrada, conviene activar **Inicio automático** y quitar la restricción de batería en la información de la app.
5. `adb shell input` y `pm clear` están bloqueados salvo que se active **Depuración USB (ajustes de seguridad)**. Las pruebas instrumentadas no los necesitan.

## Resultados del sprint de recordatorios

Ejecución del 2026-11-09 sobre la rama `sprint-4/recordatorios`.

| Verificación | Resultado |
|---|---|
| `assembleDebug` | Correcto |
| `testDebugUnitTest` | **184 de 184** |
| `lintDebug` | **0 errores**, 2 avisos intencionados |
| Pruebas instrumentadas en Xiaomi 23129RA5FL, Android 15 | **11 de 11**, en 33 s |
| Migración real de la versión 3 a la 4 en el teléfono | Sin fallos |
| Trabajos de WorkManager registrados en el teléfono | Sí |
| Notificación real de un recordatorio de tarea, con la app en segundo plano | Entregada en el teléfono |
| Permisos de la APK | No declara `INTERNET` |

### Inventario

| Clase | Nivel | Pruebas | Cubre |
|---|---|---|---|
| `ReminderRulesTest` | JVM | 11 | Horario silencioso, próximas ejecuciones y aviso de compromisos |
| `ReminderProcessorTest` | Robolectric | 9 | Entrega única, tareas terminadas, sustitución, cancelación, aplazamiento, un aviso por día |
| `AppDatabaseSchemaTest` | Instrumentada | +1 | Migración a la versión 4 |

### Limitaciones

- **Puntualidad aproximada.** WorkManager puede retrasar los avisos; HyperOS puede hacerlo más si la app no tiene inicio automático o el ahorro de batería la restringe.
- **Las pruebas automáticas usan un publicador simulado.** La notificación real y sus botones se comprueban a mano en el teléfono.

## Resultados de Sprint 3

Ejecución del 2026-11-09 sobre la rama `sprint-3/dashboard`.

| Verificación | Resultado |
|---|---|
| `assembleDebug` | Correcto |
| `testDebugUnitTest` | **164 de 164** |
| `lintDebug` | **0 errores**, 2 avisos intencionados |
| Pruebas instrumentadas en Xiaomi 23129RA5FL, Android 15 | **10 de 10**, en 35 s |
| Revisión visual de Inicio en el teléfono, sin tareas | Correcta; fecha local correcta |
| Fallos de la app en el registro del dispositivo | 0 |

### Inventario de Sprint 3

| Clase | Nivel | Pruebas | Cubre |
|---|---|---|---|
| `DashboardCalculatorTest` | JVM | 16 | Recomendación, emergencias, selección del día, indicadores, semana, alertas, próximas 24 horas |
| `DatabaseModuleTest` | JVM | 1 | El reloj usa la zona horaria del dispositivo |
| `DataStoreDailyFocusRepositoryTest` | Robolectric | 5 | Selección diaria: persistencia, caducidad y restauración |
| `TasksViewModelTest` | JVM | +1 | Abrir la vista pedida desde Inicio |
| `AppSmokeTest` | Robolectric | +1 | Inicio recomienda una tarea real y permite quitarla |
| `MainActivitySmokeTest` | Instrumentada | +1 | Inicio refleja una tarea real en el teléfono |

**Nota sobre Windows:** en JVM puro, DataStore usa un renombrado que en Windows no puede reemplazar un archivo existente. Las pruebas con varias escrituras seguidas deben usar Robolectric, que emplea el mismo método de escritura que un teléfono.

### Limitaciones de Sprint 3

- **Sin datos de Planificación.** El tiempo disponible, la sobrecarga de calendario, los proyectos con retraso y los bloques de las próximas 24 horas dependen de la Fase 4.
- **Revisión visual parcial.** Solo se revisó Inicio sin tareas; las tarjetas con contenido están cubiertas por pruebas funcionales.

## Resultados de Sprint 2

Ejecución del 2026-11-08 sobre la rama `sprint-2/tareas-prioridad`.

| Verificación | Resultado |
|---|---|
| `assembleDebug` | Correcto |
| `testDebugUnitTest` | **140 de 140** |
| `lintDebug` | **0 errores**, 2 avisos intencionados |
| Pruebas instrumentadas en Xiaomi 23129RA5FL, Android 15 | **9 de 9**, en 32 s |
| Migración real de la versión 2 a la 3 en el teléfono | Sin fallos |
| Selección y búsqueda entre 1.000 tareas en la JVM | 68 ms |
| Permisos de la APK | No declara `INTERNET` |

### Inventario de Sprint 2

| Clase | Nivel | Pruebas | Cubre |
|---|---|---|---|
| `PriorityEngineTest` | JVM | 13 | Fórmula, límites exactos, datos faltantes, manual, obligaciones, fechas, configuración |
| `TaskQueriesTest` | JVM | 15 | Las 11 vistas, los filtros, la búsqueda, el orden y 1.000 tareas |
| `TaskRulesTest` | JVM | 8 | Transiciones de estado y ciclos de dependencias |
| `TasksViewModelTest` | JVM | 8 | Vistas, búsqueda, filtros, creación, acciones y errores |
| `PrioritySettingsViewModelTest` | JVM | 6 | Formulario, validación y confirmación cuando cambian niveles |
| `OfflineTaskRepositoryTest` | Robolectric | 16 | Estados, duplicado, subtareas, dependencias, posposición, delegación, historial, configuración |
| `AppSmokeTest` | Robolectric | 12 | Se añaden creación, búsqueda, completado y prioridad sin evaluar |
| `MainActivitySmokeTest` | Instrumentada | 6 | Se añade crear y completar una tarea en teléfono real |
| `AppDatabaseSchemaTest` | Instrumentada | 3 | Se añade la migración a la versión 3, conservando tareas, etiquetas e historial |

### Limitaciones de Sprint 2

- **Rendimiento en teléfono sin medir.** Los 68 ms son de la JVM del computador; las pruebas con 500 y 1.000 tareas en dispositivo están previstas en la Fase 6.
- **Revisión visual pendiente.** Las pruebas confirman el funcionamiento, no la disposición de cada pantalla.

## Resultados de Sprint 1

Ejecución del 2026-11-08 sobre la rama `sprint-1/captura-bandeja`.

| Verificación | Resultado |
|---|---|
| `assembleDebug` | Correcto |
| `testDebugUnitTest` | **72 de 72** |
| `lintDebug` | **0 errores**, 2 avisos intencionados |
| Pruebas instrumentadas en Xiaomi 23129RA5FL, Android 15 | **7 de 7**, en 24 s |
| Migración real de la versión 1 a la 2 en el teléfono | Sin fallos |
| Permisos de la APK | No declara `INTERNET` |

Las pruebas en dispositivo usan una base en memoria (D-035), de modo que no dejan datos en la app real.

### Inventario de Sprint 1

| Clase | Nivel | Pruebas | Cubre |
|---|---|---|---|
| `CaptureRulesTest` | JVM | 11 | Fechas, duplicados, campos vacíos, división de tareas, validación |
| `QuickCaptureViewModelTest` | JVM | 8 | Restauración de borrador, título obligatorio, doble pulsación, autoguardado, fallo al guardar, plantillas |
| `InboxViewModelTest` | JVM | 5 | Agrupación, retorno de pospuestas, selección, errores |
| `DataStoreCaptureDraftRepositoryTest` | JVM | 4 | Borrador tras cierre, última categoría, fecha corrupta |
| `OfflineInboxRepositoryTest` | Robolectric | 13 | Captura, doble guardado, fecha objetivo o límite, todas las conversiones, lotes, historial |
| `AppSmokeTest` | Robolectric | 10 | Captura hasta la bandeja, ficha, conversión, plantillas, navegación |
| `MainActivitySmokeTest` | Instrumentada | 5 | Las rutas principales en un teléfono real |
| `AppDatabaseSchemaTest` | Instrumentada | 2 | Esquema y migración 1 a 2 en SQLite real |

### Limitaciones de Sprint 1

- **Tiempos de captura sin medir.** Los objetivos de 15 a 30 segundos requieren pruebas de usabilidad.
- **Rendimiento con 500 o 1.000 tareas sin medir.** Previsto en la Fase 6.
- **Pantallas anchas y Android 10** siguen sin probarse.

## Resultados de Sprint 0

Ejecución del 2026-11-08 sobre la rama `sprint-0/fundacion`.

| Verificación | Resultado |
|---|---|
| `assembleDebug` | Correcto |
| `testDebugUnitTest` | **26 de 26** |
| `lintDebug` | **0 errores**, 2 avisos intencionados |
| `assembleDebugAndroidTest` | Correcto |
| Pruebas instrumentadas en Xiaomi 23129RA5FL, Android 15 | **5 de 5**, en 20 s |
| Tema tras cierre forzado en el teléfono | **Se conserva**: abre en claro con el sistema en modo oscuro |
| Nombre accesible del botón CAPTURAR en el árbol real | Presente, en un hijo del nodo clicable |
| Permisos de la APK (`aapt2 dump permissions`) | No declara `INTERNET` |
| Fallos de la app en el registro del dispositivo | 0 |

**Avisos de lint aceptados:** `OldTargetApi`, porque el target 36 es deliberado (D-005), y `AndroidGradlePluginVersion`, porque se mantiene Gradle 9.6.0, la versión indicada para AGP 9.5.

**Aviso esperado al compilar:** el manifiesto informa de que la eliminación de `INTERNET` no encontró otra declaración. Confirma que ninguna dependencia lo solicita; la regla se mantiene como protección.

### Inventario

| Clase | Nivel | Pruebas | Cubre |
|---|---|---|---|
| `NavigationStructureTest` | JVM | 3 | Las 8 secciones del brief, sin repetición |
| `DefaultCategoriesTest` | JVM | 2 | Catálogo y UUID válidos |
| `ConvertersTest` | JVM | 3 | Conversión de `Instant` y `LocalDate`, valores ausentes |
| `ColorContrastTest` | JVM | 2 | Contraste WCAG AA en ambos temas |
| `DataStoreUserPreferencesRepositoryTest` | JVM | 4 | Valor por defecto, persistencia, valor desconocido, archivo corrupto |
| `SettingsViewModelTest` | JVM | 3 | Estado, base no disponible, guardado del tema |
| `AppDatabaseTest` | Robolectric | 3 | Siembra, reapertura sin duplicar, nombre único |
| `ExportedSchemaTest` | Robolectric | 1 | El JSON versionado coincide con las entidades |
| `AppSmokeTest` | Robolectric | 5 | Arranque real con Hilt, navegación, captura, Room, tema |
| `MainActivitySmokeTest` | Instrumentada | 4 | Las mismas rutas en dispositivo, nombre accesible del botón |
| `AppDatabaseSchemaTest` | Instrumentada | 1 | Esquema validado en SQLite real |

### Limitaciones conocidas

- **Emulador no utilizado.** No quedó en ejecución por falta de memoria o por el modo de lanzarlo, sin causa confirmada. La verificación se hizo en un teléfono real.
- **Un solo dispositivo.** No se ha probado en Android 10 (`minSdk`) ni en pantallas anchas; el riel de navegación solo tiene cobertura de compilación.
- **TalkBack no se ha ejecutado.** El nombre accesible del botón CAPTURAR se comprobó en la semántica de Compose, no en una sesión real con lector de pantalla.
- **Los tiempos de captura** (15 a 30 segundos) aún no aplican; deben medirse con pruebas de usabilidad en Fase 1.

## Plan por fase

| Fase | Pruebas previstas |
|---|---|
| 1 | Completada; ver los resultados de Sprint 1 |
| 2 | Completada; ver los resultados de Sprint 2 |
| 3 | Completada; ver los resultados de Sprint 3 |
| 4 | Progreso ponderado, solapamiento de bloques, capacidad |
| 5 | Revisiones, métricas y definición de cada indicador |
| 6 | 500 y 1.000 tareas, datos corruptos, almacenamiento insuficiente, cierre inesperado, exportación y restauración |
