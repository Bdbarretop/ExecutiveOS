# EXECUTIVE OS

Sistema personal de gestión ejecutiva para Android: Kotlin, Compose, Material 3, Hilt, Room, DataStore. Offline-first y privado por diseño.

La especificación íntegra está en `docs/REQUIREMENTS.md`. Léela antes de empezar una fase.

## Reglas de trabajo

- **Se avanza por fases y se para entre ellas.** No se empieza la siguiente sin aprobación expresa del usuario. El estado está en `docs/ROADMAP.md`.
- **No se declara implementada una función si solo existe su interfaz.** Al cerrar una fase se informa de: qué se hizo, archivos, decisiones, pruebas, resultado de compilación, errores pendientes, limitaciones y próximo paso.
- No se continúa a otro módulo con errores críticos en el actual.
- Sin funciones vacías ni datos ficticios en producción.
- Decisiones técnicas con motivo en `docs/DECISIONS.md`.
- Antes de decir que algo está corriendo, comprueba que el proceso sigue activo. En esperas largas, muestra el progreso y detecta pronto los bloqueos: el usuario quiere ver avance real.
- Guarda el trabajo en commits a medida que se termina cada bloque, no al final.
- Para probar en el teléfono Xiaomi del usuario, sigue la preparación descrita en `docs/TEST_PLAN.md`.

## Comandos

En este equipo `java` del PATH es JRE 8: define siempre `JAVA_HOME` para esa terminal.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebugAndroidTest
```

Las pruebas instrumentadas requieren dispositivo: `.\gradlew.bat connectedDebugAndroidTest`. El equipo tiene 8 GB de RAM; ejecuta `.\gradlew.bat --stop` antes de abrir el emulador.

## Arquitectura

Módulo único `:app`, paquete raíz `com.executiveos`. Detalle en `docs/ARCHITECTURE.md`.

- `feature/*` no importa `data`; usa interfaces de `domain`.
- `domain` no importa Android, Room ni Compose.
- Los paquetes se crean cuando tienen código.
- Las rutas son objetos `@Serializable` en `core/navigation/Destinations.kt`.
- AGP 9 integra Kotlin: **no apliques** `org.jetbrains.kotlin.android` en `:app`, y no uses `kapt`, solo KSP.

## Base de datos

- Todo cambio de esquema sube `AppDatabase.SCHEMA_VERSION`, añade migración explícita con prueba y versiona el JSON de `app/schemas/`.
- Nunca uses `fallbackToDestructiveMigration` ni `OnConflictStrategy.REPLACE` sobre datos del usuario.
- UUID como clave, sin valores inventados para campos vacíos. Convenciones en `docs/DATA_MODEL.md`.

## Privacidad

- La APK no debe declarar `INTERNET` durante el MVP; compruébalo con `aapt2 dump permissions`.
- Sin datos personales en logs.
- Sin criptografía propia.
- Sin secretos, claves ni exportaciones en Git.

## Idioma y estilo

- Interfaz y documentación en español; identificadores de código en inglés.
- Textos de interfaz siempre en `res/values/strings.xml`.
- En `androidTest` no uses nombres de función con espacios: `minSdk` 29 no los admite.

## Git

Commits lógicos por funcionalidad, una rama por sprint y sin fusionar a `main` sin aprobación.
