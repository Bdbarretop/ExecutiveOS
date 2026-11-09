# EXECUTIVE OS

Sistema personal de dirección y ejecución para Android. Ayuda a capturar compromisos, entender qué importa y convertirlo en acciones concretas. Funciona sin conexión y los datos no salen del dispositivo.

> **Estado: Sprint 1 (Captura y bandeja).** Funcionan la captura rápida, la ficha completa, la bandeja con su procesamiento y las plantillas. **Las vistas de tareas, la prioridad calculada, el dashboard y los proyectos aún no están construidos**; sus secciones lo indican en pantalla y dicen en qué fase llegan. Avance en [docs/ROADMAP.md](docs/ROADMAP.md).

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

## Instalar

Con un dispositivo o emulador conectado:

```powershell
.\gradlew.bat installDebug
```

## Pruebas

| Comando | Qué ejecuta | ¿Requiere dispositivo? |
|---|---|---|
| `.\gradlew.bat testDebugUnitTest` | Pruebas JVM y Robolectric | No |
| `.\gradlew.bat lintDebug` | Análisis estático | No |
| `.\gradlew.bat connectedDebugAndroidTest` | Pruebas instrumentadas | Sí |

## Documentación

| Documento | Contenido |
|---|---|
| [PRODUCT_VISION](docs/PRODUCT_VISION.md) | Qué es y qué principios sigue |
| [REQUIREMENTS](docs/REQUIREMENTS.md) | Especificación íntegra y trazabilidad |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | Capas, dependencias y seguridad |
| [DATA_MODEL](docs/DATA_MODEL.md) | Esquema y convenciones |
| [CAPTURE_UX](docs/CAPTURE_UX.md) | Diseño de la captura |
| [ROADMAP](docs/ROADMAP.md) | Fases y puntos abiertos |
| [TEST_PLAN](docs/TEST_PLAN.md) | Estrategia y resultados |
| [DECISIONS](docs/DECISIONS.md) | Decisiones técnicas y su motivo |

## Privacidad

Sin publicidad, seguimiento ni servidores externos. La APK no declara permiso de internet y las copias automáticas de Android están desactivadas.
