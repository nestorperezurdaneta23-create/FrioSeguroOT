# Verificación automatizada

Fecha: 14 de octubre de 2026.

- Compilación del APK: correcta con JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.10 y SDK 35.
- Firma APK: verificada con apksigner. Firma de desarrollo Android Debug.
- Núcleo JVM: 20 pruebas ejecutadas, 0 fallos y 0 errores.
- Comunicación por sockets TCP en loopback del computador: 1000 respuestas correctas de 1000; mediana 116 microsegundos; p95 498 microsegundos; máximo 7351 microsegundos.
- Android Lint: 0 errores, 19 advertencias de convenciones KTX y localización de textos de la app en español.
- Distribución Gradle: SHA256 comprobado contra el archivo oficial; checksum fijado en el wrapper.

Los resultados XML y el informe Lint están en esta carpeta. Los ensayos del computador no son pruebas de Wi-Fi. Las pruebas instrumentadas Android y su alcance se registran en android/instrumentacion.txt. Las pruebas con dos teléfonos físicos deben completarse en Registro_pruebas_wifi.csv.

Para reproducir: ./gradlew :app:assembleDebug :core:test :app:lintDebug. Para Android: ./gradlew :app:connectedDebugAndroidTest en un dispositivo de prueba. Las pruebas instrumentadas borran la cuenta local del dispositivo usado para probar.

## Android API 28

Tres pruebas de credenciales aprobadas: persistencia cifrada con Keystore, rechazo de contraseña débil y bloqueo después de cinco intentos fallidos. Los tres resultados están en android/instrumentacion_inicial.txt.

La prueba final de integración local aprobó login, recepción de telemetría, alarma, orden MANUAL con ACK y fallback AUTO con consigna 4 °C. Su registro es android/instrumentacion.txt. Usa dos modelos de app dentro del mismo emulador. El login y la navegación se activan mediante eventos programáticos de vistas Android; la orden se comprueba en el modelo y en el par receptor. Las cuatro imágenes se renderizaron desde las vistas Android con datos del ensayo. No son fotografías de dos teléfonos ni acreditan gestos táctiles.

Los intentos iniciales de interfaz encontraron falta de foco en el display del emulador, antes de ingresar las credenciales. Se conservan instrumentacion_inicial.txt e instrumentacion_foco.txt. La prueba final evita depender del foco global y verifica los eventos de vistas y el contrato de comunicación. El acceso táctil y la Wi-Fi física deben verificarse con el protocolo de dos teléfonos.
