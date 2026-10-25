# FríoSeguro OT

Aplicación Android en Kotlin para monitorear y controlar una cámara de refrigeración simulada desde dos teléfonos conectados por Wi-Fi. Proyecto individual de Néstor Urdaneta para TI3042, Unidad 2, evaluación sumativa.

**Versión 1.1, paquete final actualizado el 25 de octubre de 2026.** Incluye autenticación SQLite, historial persistente y consultable, exportación CSV y transporte TLS con identidad del Equipo verificada. El repositorio público es [https://github.com/nestorperezurdaneta23-create/FrioSeguroOT](https://github.com/nestorperezurdaneta23-create/FrioSeguroOT).

El teléfono **Equipo** produce temperatura, puerta y estado del compresor. El teléfono **Operador** recibe datos, muestra alertas y envía órdenes con ACK. Los sensores y el actuador son simulados. Los rangos son didácticos.

## Instalar y conectar dos teléfonos

1. Instala `apk/FrioSeguroOT.apk` en ambos teléfonos con Android 8 o posterior. Ambos deben usar la versión 1.1.
2. La APK final fue firmada de nuevo. Si tienes instalada una APK de demostración anterior (1.0 o la 1.1 preliminar) y Android rechaza la actualización por firma distinta, exporta primero sus evidencias y desinstálala antes de instalar esta; deberás crear la cuenta otra vez. Desinstalar elimina los datos locales. El código de migración a SQLite se aplica a compilaciones que conservan la misma firma y los datos.
3. Conecta ambos teléfonos a la misma Wi-Fi WPA2/WPA3, sin aislamiento entre clientes. También sirve un hotspot de un tercer teléfono. Activa fecha y hora automáticas.
4. Crea una cuenta local en cada dispositivo. Usuario de 3 a 32 caracteres; contraseña de 12 a 128 caracteres, con letras y números. No hay credenciales predeterminadas. Inicia sesión.
5. En B selecciona **Equipo**, configura **Permitir control remoto** y pulsa **Iniciar equipo**. Espera el estado **Equipo listo · TLS obligatorio**.
6. Copia su IP privada. Usa **Copiar código privado** o revela el campo para transferir el **código FS2 completo**, por un medio de confianza, al teléfono A. No lo publiques. El código incluye un secreto de 128 bits y la huella SHA256 del certificado de B.
7. En A selecciona **Operador**, ingresa IP y código y pulsa **Conectar**. La app verifica TLS y autentica el emparejamiento. El estado muestra la versión TLS negociada.
8. Abre **Monitoreo**. En B simula temperatura alta o puerta abierta y comprueba la alerta recibida en A. Desde **Activador**, envía una orden, confirma y revisa su ACK y el cambio en B.

No se necesita Internet. Mantén ambas apps en primer plano. Pasar a segundo plano cierra el enlace; cinco minutos sin interacción cierran la sesión local. Un nuevo inicio del Equipo genera otro código. Tras perder una sesión, el Operador reconecta de forma explícita y no se reenvían órdenes automáticamente.

## Guardar y consultar datos

El Equipo guarda una lectura LOCAL por ciclo. El Operador guarda cada telemetría REMOTA aceptada. SQLite conserva las últimas **10.000 lecturas por instalación**, con fecha de captura, fecha de recepción, origen, alarma y un payload cifrado.

En **Historial** puedes consultar todos los orígenes o seleccionar Locales/Remotos, limitar a los últimos 15 minutos o a Hoy y mostrar solo alarmas. La pantalla presenta las 50 coincidencias más recientes y actualiza la consulta cada cinco segundos mientras permanece abierta. Funciona después de reiniciar y sin conectividad.

**Exportar consulta a CSV** incluye todas las coincidencias conservadas, hasta 10.000, con fechas UTC ISO 8601, grados Celsius y columnas estables. El archivo se guarda en la ubicación que elijas. La base privada y los secretos no se exportan. El selector de archivos puede pasar la app a segundo plano y cerrar el enlace; conserva primero las evidencias de esa sesión.

La bitácora de **Registro** guarda los últimos 200 eventos de la ejecución y se exporta aparte. El historial de sensores sí persiste en SQLite. Los contadores de órdenes son acumulativos de esta ejecución; las muestras y bytes corresponden al enlace actual. Los bytes indicados son tramas de aplicación y excluyen el overhead TLS/TCP/IP.

## Herramientas y compilación

- Android Studio compatible con AGP 8.9; JDK 17; Android SDK Platform 35 y Build Tools 35.0.0.
- Kotlin 2.1.10, AGP 8.9.2 y Gradle 8.11.1, con versiones y checksum del wrapper fijados.
- UI nativa Material Components, ViewModel y LiveData; SQLiteOpenHelper de Android; SSLSocket/SSLServerSocket y Android Keystore.
- Abre la carpeta que contiene `settings.gradle.kts`. Android Studio creará `local.properties` con la ruta del SDK.

Linux/macOS:

```bash
chmod +x gradlew
./gradlew :app:assembleDebug :core:test :app:lintDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :core:test :app:lintDebug
```

El APK compilado queda en `app/build/outputs/apk/debug/app-debug.apk`. El adjunto está firmado para desarrollo. Revisa `apk/SHA256.txt` para verificar el archivo distribuido.

Las pruebas Android deben ejecutarse en un dispositivo de prueba: borran su cuenta y base local. Para la batería principal:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cl.nestor.frioseguro.AccountSecurityTest,cl.nestor.frioseguro.HistoryStorageTest,cl.nestor.frioseguro.TlsTransportTest,cl.nestor.frioseguro.UiIntegrationTest
```

La prueba de persistencia entre procesos se ejecuta con `RestartPersistenceTest#seed`, una detención del proceso y luego `RestartPersistenceTest#verify` en otra instrumentación. El alcance y los resultados obtenidos se registran en `evidencias/automatizadas/VERIFICACION.md`.

## Estructura

| Ruta | Función |
| --- | --- |
| `app/.../MainActivity.kt` | Login, Conexión, Monitoreo, Activador, Registro e Historial |
| `app/.../AuthStore.kt` | Autenticación SQLite, migración 1.0, PBKDF2 y bloqueo |
| `app/.../LocalDatabase.kt` y `HistoryStore.kt` | Esquema SQL, transacciones, filtros, retención y CSV |
| `app/.../DeviceVault.kt` | Cifrado AES GCM con claves Android Keystore |
| `app/.../TlsTransport.kt` y `WifiLink.kt` | TLS 1.2/1.3 y certificado verificado mediante el código FS2 |
| `app/.../AppModel.kt` | Captura, persistencia asíncrona, telemetría, órdenes y métricas |
| `core` | Simulación, protocolo PSK dentro de TLS, validación de identidad y pruebas JVM |
| `docs` | Informe, diseño, seguridad, almacenamiento y protocolo de pruebas |
| `evidencias` | Resultados verificables y registro pendiente de ensayos Wi-Fi físicos |

## Entrega en AAI

Sube **`Entrega_TI3042_Nestor_Urdaneta.txt`**, incluido en la raíz. Contiene el enlace real al repositorio. Confirma con **Enviar tarea**. La nueva guía mantiene este formato de entrega.

La demostración debe cubrir login SQLite, enlace entre ambos Android, almacenamiento, consulta después de reiniciar, CSV y controles de seguridad. Completa `docs/PRUEBAS_DISPOSITIVOS.md` y `evidencias/Registro_pruebas_wifi.csv` con mediciones reales. La validación del tema, diseño y autenticación por el docente debe obtenerse realmente, incluida la continuidad con Unidad 1 si corresponde.

## Alcance de seguridad

Las credenciales y los valores de sensores se cifran antes de guardarlos; SQLite conserva metadatos de hora, origen y alarma para consultar e indexar. TLS se valida con la huella entregada por el Equipo en el código FS2, sin aceptar certificados arbitrarios ni recurrir a TCP sin TLS. Encima de TLS se conserva la autenticación PSK y las tramas AES GCM del prototipo.

La solución aplica objetivos de ISO/IEC 27400 e ISO/IEC 27001 y consideraciones OT. No declara certificación, no integra un PLC o refrigerador real y no sustituye controles de seguridad de una planta. Revisa `docs/SEGURIDAD.md`.
