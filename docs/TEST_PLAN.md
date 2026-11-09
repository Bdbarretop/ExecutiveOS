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
4. `adb shell input` y `pm clear` están bloqueados salvo que se active **Depuración USB (ajustes de seguridad)**. Las pruebas instrumentadas no los necesitan.

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
| 1 | Captura, autoguardado, doble pulsación, conversión de capturas sin pérdida, migración 1 a 2 |
| 2 | Cálculo de prioridad, estados, fechas, filtros, búsqueda, dependencias circulares |
| 3 | Indicadores y alertas con datos reales, base vacía |
| 4 | Progreso ponderado, solapamiento de bloques, capacidad |
| 5 | Revisiones, métricas y definición de cada indicador |
| 6 | 500 y 1.000 tareas, datos corruptos, almacenamiento insuficiente, cierre inesperado, exportación y restauración |
