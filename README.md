# EXECUTIVE OS

Sistema personal de dirección y ejecución para Android. Ayuda a capturar compromisos, entender qué importa y convertirlo en acciones concretas. Funciona sin conexión y los datos no salen del dispositivo.

> **Estado: MVP 1.0.0.** Funcionan la captura, la bandeja, las tareas con motor de prioridad, el panel de Inicio, los proyectos con hitos ponderados, la planificación por bloques de tiempo, las revisiones, la formación, las métricas, los recordatorios locales y la copia de seguridad cifrada. Avance en [docs/ROADMAP.md](docs/ROADMAP.md).

## Requisitos

| Herramienta | Versión |
|---|---|
| Android Studio | **Rabbit 2 (2026.2.2) o superior** para abrir el proyecto. Las versiones anteriores no admiten AGP 9.5 |
| JDK | 17 o superior; el JBR incluido en Android Studio sirve |
| Android SDK | Plataforma 37 y build-tools 36.0.0 (Gradle puede instalarlos) |
| Dispositivo | Android 10 o superior |

## Compilar en Windows

Crea `local.properties` en la raíz si no existe:

```properties
sdk.dir=C\:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk
```

Define `JAVA_HOME` en la terminal, necesario si `java` del PATH es anterior a la versión 17:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
```

Compila la APK de depuración:

```powershell
.\gradlew.bat assembleDebug
```

Resultado: `app/build/outputs/apk/debug/app-debug.apk`.

> Mantén el proyecto en una ruta sin tildes y fuera de carpetas sincronizadas como OneDrive. AGP rechaza las rutas no ASCII en Windows.

Para la APK optimizada:

```powershell
.\gradlew.bat assembleRelease
```

Resultado: `app/build/outputs/apk/release/app-release.apk`. Sin `keystore.properties` se firma con la clave de depuración local, válida para uso personal pero no para distribuirla ([D-079](docs/DECISIONS.md)). Para usar una clave propia, crea en la raíz un `keystore.properties`, que Git ignora, con `storeFile`, `storePassword`, `keyAlias` y `keyPassword`.

## Instalar

Copia la APK al teléfono y ábrela, o con el dispositivo conectado:

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

`-r` actualiza conservando los datos. **No desinstales la app sin hacer antes una copia de seguridad**: al desinstalar se borran todos los datos. El uso de la app se explica en el [manual](docs/USER_GUIDE.md).

## Pruebas

| Comando | Qué ejecuta | ¿Requiere dispositivo? |
|---|---|---|
| `.\gradlew.bat testDebugUnitTest` | Pruebas JVM y Robolectric | No |
| `.\gradlew.bat lintDebug` | Análisis estático | No |
| `.\gradlew.bat connectedDebugAndroidTest` | Pruebas instrumentadas | Sí |

## Documentación

| Documento | Contenido |
|---|---|
| [USER_GUIDE](docs/USER_GUIDE.md) | Manual básico de usuario |
| [FUNCTIONAL](docs/FUNCTIONAL.md) | Qué hace cada módulo y con qué reglas |
| [PRODUCT_VISION](docs/PRODUCT_VISION.md) | Qué es y qué principios sigue |
| [REQUIREMENTS](docs/REQUIREMENTS.md) | Especificación íntegra y trazabilidad |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | Capas, dependencias y seguridad |
| [DATA_MODEL](docs/DATA_MODEL.md) | Esquema y convenciones |
| [CAPTURE_UX](docs/CAPTURE_UX.md) | Diseño de la captura |
| [ROADMAP](docs/ROADMAP.md) | Fases y puntos abiertos |
| [TEST_PLAN](docs/TEST_PLAN.md) | Estrategia y resultados |
| [DECISIONS](docs/DECISIONS.md) | Decisiones técnicas y su motivo |

## Privacidad

Sin publicidad, seguimiento ni servidores externos. La APK no declara permiso de internet y las copias automáticas de Android están desactivadas. Las copias de seguridad las crea la persona y pueden cifrarse con contraseña.
