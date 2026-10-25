# Verificación de FríoSeguro OT 1.1

Paquete final reconstruido el 25 de octubre de 2026 a partir del código corregido. No se ha cambiado la lógica Kotlin de la versión validada el 24 de octubre.

## Comprobaciones del paquete final

- APK de la aplicación y APK de instrumentación compiladas correctamente con JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.10 y SDK 35.
- 29 pruebas JVM aprobadas: 8 PlantTest, 12 SecureChannelTest y 9 PairingTlsTest; 0 fallos y 0 errores. XML originales de esta reconstrucción en esta carpeta.
- PairingTlsTest establece TLS 1.2 y TLS 1.3 en loopback con SecureChannel y rechaza huellas incorrectas, certificados inválidos y códigos antiguos. No mide Wi-Fi.
- Firma Android Debug verificada con apksigner; salida original en Firma_APK.txt. SHA256 del APK en apk/SHA256.txt y RESUMEN_BUILD.json. La nueva firma de demostración puede exigir reinstalar sobre APK anteriores.
- Android Lint: 0 errores y 30 advertencias. Los textos de la app están en español; hay observaciones SetTextI18n, UseKtx, ApplySharedPref y CustomX509TrustManager. La confianza TLS propia verifica la huella y certificado recibido fuera de banda, no acepta certificados arbitrarios.
- SHA256 de Gradle 8.11.1 contrastado con el checksum fijado en el wrapper. La CLI Android se verificó con SHA1 de los metadatos oficiales de Google y el JDK con su checksum oficial.

## Validación Android anterior

El 24 de octubre aprobaron 12 casos distintos en Android API 28 emulado: 4 de cuenta, 5 de historial, 2 de TLS y 1 de integración. Se aprobaron además seed y verify en procesos separados. El primer lote detectó dos fallos; después de corregir la autorización de firma de la identidad TLS se repitieron los tres casos de TLS e integración y aprobaron.

El detalle y los límites están en android/RESUMEN_ANDROID_2026-10-24.md. Se presenta como resumen de resultados observados, no como un log original reconstruido. Los archivos temporales y seis capturas de 1.1 se perdieron antes de publicarse; no se incluyen. No se repitió la instrumentación Android el 25 de octubre.

Los logs instrumentacion.txt, instrumentacion_inicial.txt e instrumentacion_foco.txt y las cuatro imágenes de android/qa pertenecen a 1.0. Son antecedentes, no evidencias de SQLite/TLS de 1.1.

## Alcance y reproducción

Los ensayos JVM y un emulador no acreditan dos teléfonos ni Wi-Fi física. T01 a T23 de docs/PRUEBAS_DISPOSITIVOS.md y evidencias/Registro_pruebas_wifi.csv siguen PENDIENTE hasta ejecutarlos realmente. La aprobación del docente también debe obtenerse.

Compilar y revisar: ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :core:test :app:lintDebug. En un Android de prueba ejecutar AccountSecurityTest, HistoryStorageTest, TlsTransportTest y UiIntegrationTest. Ejecutar RestartPersistenceTest#seed y #verify en instrumentaciones separadas con am force-stop entre ambas; ver README.md. Estos tests preparan o borran la cuenta y lecturas del dispositivo de prueba.
