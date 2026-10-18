# FríoSeguro OT

Prototipo Android para monitorear y controlar una cámara de refrigeración simulada mediante dos teléfonos conectados a una red Wi-Fi local. Proyecto de Néstor Urdaneta para TI3042, Unidad 2.

Una instalación actúa como **Equipo**: simula temperatura, puerta y compresor. La otra actúa como **Operador**: recibe telemetría, visualiza alarmas y envía órdenes con confirmación. El tráfico de aplicación se cifra y autentica. No se necesita un servicio de Internet.

## Inicio rápido en dos teléfonos

1. Instala el mismo APK en dos dispositivos con Android 8 o posterior. Es un APK de demostración firmado para desarrollo; no es una distribución de producción.
2. Conecta los teléfonos a la misma red Wi-Fi, sin aislamiento entre clientes. También puedes usar el hotspot de un tercer teléfono. Activa la hora automática en ambos.
3. Abre la app en cada uno y crea una cuenta local. Usa un usuario de 3 a 32 caracteres y una contraseña de al menos 12 caracteres, con letras y números. No hay credenciales predeterminadas.
4. Inicia sesión. En el teléfono B selecciona **Equipo**, configura **Permitir control remoto** y pulsa **Iniciar equipo**.
5. Copia la IP local que muestra B. Revela su código con el icono del ojo. Transcribe los 32 caracteres en A, que debe estar en modo **Operador**, y pulsa **Conectar**. El código también acepta espacios o guiones como separadores.
6. En A abre **Monitoreo**. Debes ver una muestra nueva aproximadamente cada segundo. En B abre Monitoreo y pulsa **Simular temperatura alta** o activa **Puerta abierta**. Comprueba que A recibe la alerta.
7. En A abre **Activador**: selecciona AUTO o MANUAL, ajusta el slider y el switch, pulsa **Enviar orden** y confirma. Verifica el mensaje de confirmación y el cambio en B.
8. Prueba la pérdida de Wi-Fi y el permiso de solo monitoreo. Exporta la bitácora desde **Registro**, antes de cerrar o reiniciar la app.

Mantén la app en primer plano en ambos teléfonos. Al pasar a segundo plano, el enlace se cierra; esto es deliberado. Al detener e iniciar B se genera un código nuevo. Para reconectar A debes ingresar el código vigente. Si B sigue esperando con la misma ejecución, el código no cambia.

## Abrir y compilar en Android Studio

- Abre la carpeta que contiene `settings.gradle.kts`; no solo la carpeta `app`.
- JDK 17, Android SDK Platform 35 y Build Tools 35.0.0.
- Android Gradle Plugin 8.9.2, Gradle 8.11.1, Kotlin 2.1.10. Las versiones están fijadas.
- Android Studio Meerkat o una versión compatible con AGP 8.9.
- Espera la sincronización de Gradle. Android Studio generará `local.properties` con tu ruta del SDK.
- Ejecuta la app en ambos teléfonos o usa **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.

Linux o macOS:

```bash
chmod +x gradlew
./gradlew :app:assembleDebug :core:test :app:lintDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :core:test :app:lintDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. Las pruebas de credenciales con Android Keystore se ejecutan en un dispositivo de prueba con `./gradlew :app:connectedDebugAndroidTest`. Estas pruebas borran la cuenta local del dispositivo de prueba antes de ejecutarse.

## Qué contiene el proyecto

| Ruta | Contenido |
| --- | --- |
| `app/src/main/java/.../MainActivity.kt` | Login y pantallas Material de Conexión, Monitoreo, Activador y Registro |
| `app/src/main/java/.../AuthStore.kt` | Cuenta local, hash PBKDF2 y almacenamiento cifrado con Android Keystore |
| `app/src/main/java/.../WifiLink.kt` | Conexión TCP local, límites y cierre de sesión |
| `app/src/main/java/.../AppModel.kt` | Telemetría, validación de órdenes, métricas y bitácora |
| `core/src/main/kotlin/.../SecureChannel.kt` | Autenticación de pares y tramas AES GCM con anti repetición |
| `core/src/main/kotlin/.../Plant.kt` | Simulación y control local con histéresis |
| `core/src/test` | Pruebas de control, cifrado y sockets locales |
| `app/src/androidTest` | Pruebas de cuenta y Keystore en Android |
| `docs` | Informe, diseño, seguridad, pauta y protocolo de pruebas |
| `evidencias` | Resultados automatizados y formulario para pruebas Wi-Fi reales |
| `scripts/crear_entrega.py` | Genera el bloc de notas exigido a partir del enlace real de GitHub |

## Entrega exigida por el Word

El archivo que se sube al AAI debe ser un **bloc de notas con el enlace a GitHub**. El ZIP y el APK son apoyos para preparar y demostrar el proyecto.

El proyecto está publicado en [https://github.com/nestorperezurdaneta23-create/FrioSeguroOT](https://github.com/nestorperezurdaneta23-create/FrioSeguroOT), con acceso público para la revisión docente.

1. Descarga `Entrega_TI3042_Nestor_Urdaneta.txt`, incluido en la raíz del proyecto. Contiene el enlace real a este repositorio.
2. Sube ese `.txt` al AAI y confirma con **Enviar tarea**. No basta con dejar la entrega como borrador.
3. Instala `apk/FrioSeguroOT.apk` en dos teléfonos y completa `docs/PRUEBAS_DISPOSITIVOS.md`. Registra resultados reales en `evidencias/Registro_pruebas_wifi.csv` y guarda capturas y bitácoras sin secretos.
4. Revisa `docs/Informe_FrioSeguro_OT.docx`, la matriz de la pauta y las evidencias automatizadas. Las pruebas de teléfonos físicos y la validación del docente siguen pendientes hasta ejecutarlas realmente.

Para volver a generar el archivo de entrega: `python scripts/crear_entrega.py https://github.com/nestorperezurdaneta23-create/FrioSeguroOT`.
No publiques `local.properties`, contraseñas, códigos de emparejamiento, claves de firma ni carpetas `build`.

Antes de la evaluación muestra el problema, la elección de Wi-Fi y el diseño al docente. La validación del docente y los resultados de dispositivos físicos deben obtenerse realmente; el proyecto no los da por realizados.

## Alcance

Las temperaturas, los umbrales y la dinámica son didácticos. El prototipo no incluye un sensor, PLC, refrigerador real, servicio en segundo plano ni certificación ISO. La documentación relaciona controles concretos con objetivos de ISO/IEC 27400 e ISO/IEC 27001; no declara certificación ni cumplimiento integral de las normas.
