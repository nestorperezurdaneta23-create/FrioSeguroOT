# Validación Android de FríoSeguro OT 1.1

Fecha de ejecución: 24 de octubre de 2026. Entorno: Android API 28 emulado, sin aceleración KVM. Este resumen documenta los resultados observados durante aquella sesión; no es una salida JUnit generada automáticamente.

## Casos aprobados

| Grupo | Casos | Resultado y alcance |
| --- | ---: | --- |
| AccountSecurityTest | 4 | Cuenta cifrada persistente, contraseña débil, bloqueo tras cinco fallos y migración de preferencias a SQLite |
| HistoryStorageTest | 5 | Persistencia cifrada, filtros y CSV, entradas inválidas, alteración de metadatos y retención de 10.000 filas |
| TlsTransportTest | 2 | TLS real con clave no exportable de Keystore y rechazo de una huella distinta por la causa de identidad |
| UiIntegrationTest | 1 | Login, telemetría TLS, alarma, orden MANUAL con ACK, Historial SQLite y fallback AUTO a 4 °C |
| Total de casos distintos | 12 | Aprobados entre el lote inicial de cuenta/historial y la repetición final de TLS/integración |

El primer lote ejecutó 12 casos y detectó dos fallos, de handshake y de integración. Se autorizó DIGEST_NONE para que Conscrypt firmara el resumen TLS previamente calculado y se renovó el alias de identidad. Se repitieron los dos casos TLS y la integración: resultado OK (3 tests), tiempo informado 160.73 s. La prueba de huella exige una CertificateException por identidad, de modo que otro fallo de handshake no produzca un falso positivo.

## Persistencia entre procesos

RestartPersistenceTest#seed aprobó en una instrumentación (2.252 s). Después se detuvo el proceso mediante am force-stop y RestartPersistenceTest#verify aprobó en otra (27.526 s). Se conservaron la cuenta evaluacion y una lectura REMOTO con muestra 999991 y 9.75 °C. Son datos de prueba simulados.

## Evidencia conservada y límites

Los logs y las seis capturas temporales de 1.1 se perdieron al desconectarse y limpiarse el entorno antes de su publicación. Se conserva este resumen de la ejecución y los tests reproducibles en app/src/androidTest. No se han reconstruido ni presentado como originales logs XML o capturas que ya no están disponibles.

La integración utilizó dos modelos de app en un solo emulador. El login y la navegación se activaron mediante eventos programáticos de vistas Android. No acredita gestos táctiles, dos teléfonos ni Wi-Fi física. La retención preparó filas por SQL y comprobó el guardado real al superar el límite; no equivale a varias horas de captura.

La reconstrucción del paquete final el 25 de octubre repite compilación, pruebas JVM, firma y Lint. No repite esta instrumentación Android; los cambios posteriores a la validación son documentos y empaquetado, y la lógica Kotlin del enlace y almacenamiento es la misma. El registro físico T01 a T23 sigue PENDIENTE hasta probarlo realmente.
