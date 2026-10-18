# Seguridad de FríoSeguro OT

## Activos y amenazas

Se protegen las credenciales locales, el secreto de emparejamiento, la integridad de las órdenes, la telemetría y la disponibilidad del control local. Se contemplan observación del tráfico en la LAN, suplantación de un operador, manipulación o repetición de mensajes, acceso al almacenamiento local y pérdida de conectividad.

El teléfono Equipo decide si una sesión recibe control o solo lectura. Poseer un código válido concede acceso al equipo dentro del permiso que este configuró; las cuentas locales no constituyen una identidad centralizada ni un sistema multiusuario de planta. El código debe compartirse presencialmente o por un canal confiable.

## Controles implementados

| Objetivo | Implementación | Verificación |
| --- | --- | --- |
| Acceso local | Cuenta creada por el usuario, mínimo 12 caracteres; sin contraseña predefinida | Cuenta válida, contraseña incorrecta y clave débil |
| Protección de credenciales | PBKDF2 HMAC SHA256, 210000 iteraciones y sal aleatoria de 16 B; el usuario, sal y hash se cifran con AES 256 GCM usando Android Keystore | Pruebas instrumentadas de persistencia y ausencia de texto claro |
| Fuerza bruta local | Bloqueo de 30 s después de 5 fallos, persistido en preferencias privadas | Prueba instrumentada de bloqueo entre instancias |
| Emparejamiento | Código aleatorio de 128 bits, 32 caracteres hexadecimales; no se transmite la PSK | Código incorrecto rechazado |
| Autenticación mutua | Nonce de 32 B por par y HMAC SHA256 sobre el intercambio y el permiso | Negociación correcta y rechazo de clave incorrecta |
| Cifrado de sesión | HKDF SHA256 y claves AES 256 GCM separadas por dirección | Intercambio bidireccional y búsqueda de texto claro en tramas |
| Anti repetición | Secuencia estricta por dirección; IV de 12 B formado por prefijo de 4 B y contador de 8 B; datos asociados incluyen versión y secuencia | Reenvío de trama rechazado |
| Integridad | Etiqueta GCM de 128 bits | Alteración de trama rechazada |
| Autorización de control | Permiso concedido por el Equipo y validado al recibir cada orden | Prueba de comando no autorizado y sesión solo lectura |
| Límites de entrada | Tramas de hasta 16384 B, órdenes dentro de rangos, identificadores acotados, datos finitos | Pruebas de tamaño, NaN, modo y rango |
| Vigencia de órdenes | Marca temporal válida durante 60 s, identificador sin duplicados recientes | Revisión de AppModel y ensayo con hora automática |
| Disponibilidad OT | AUTO con consigna 4 °C al perder el enlace, sin reenvío de órdenes, cola de salida de 8 elementos | Prueba de fallback y ensayo de pérdida de Wi-Fi |
| Sesión | Cinco minutos sin interacción cierran login; paso a segundo plano cierra enlace | Ensayo de inactividad y cambio de app |
| Privacidad | Sin analítica ni envío de credenciales; secretos ocultos y pantalla de acceso/conexión protegida de capturas; backup deshabilitado | Revisión de manifiesto y bitácora |
| Trazabilidad | Eventos y métricas exportables, últimos 200 eventos por ejecución, sin contraseñas ni PSK | Exportación en escenario real |

## Relación con estándares

ISO/IEC 27400:2022 ofrece lineamientos sobre riesgos y controles de seguridad y privacidad IoT. ISO/IEC 27001:2022 trata un sistema de gestión de seguridad de la información. Los controles anteriores son una aplicación académica de esos objetivos y de la gestión de riesgos; no son una certificación ni una auditoría de todas sus cláusulas. La evaluación OT debe priorizar disponibilidad, integridad de control y seguridad del proceso, además de confidencialidad.

La red de demostración debe usar WPA2 o WPA3 y permanecer separada de redes industriales. Configura el acceso al puerto 8842 únicamente dentro de la LAN y no hagas redirección de puertos al Internet. La segregación de red y la protección Wi-Fi se configuran en el router, no se atribuyen a la app.

## Límites y evolución

La sesión usa un protocolo académico PSK con primitivas estándar, no TLS ni un protocolo industrial certificado. No aporta secreto hacia adelante: si se revela una PSK de una ejecución, un adversario con sus capturas podría derivar claves de esas sesiones. La clave desaparece del uso de la app al detener; no se guarda en disco, aunque la eliminación de cadenas en memoria no puede garantizarse por el runtime.

Un usuario con el código puede intentar una sesión autorizada; no hay listas centralizadas de usuarios, revocación por identidad ni autenticación biométrica. Los dispositivos rooteados o comprometidos y la denegación de servicio de la red quedan fuera del modelo de protección. Un peer autenticado que deje de leer podría bloquear una escritura TCP; la cola está acotada y el cierre de socket permite recuperar la ejecución. El cierre automático de sesión al perder comunicación depende del timeout de lectura de 7 s.

La bitácora no es un registro inmutable ni persiste automáticamente entre ejecuciones. Para uso industrial se requieren revisión criptográfica independiente, TLS o protocolo industrial adecuado, credenciales administradas, watchdog del actuador físico, auditoría persistente protegida, gestión de cambios y evaluación formal del riesgo del proceso.

## Fuentes

- ISO. ISO/IEC 27400:2022. https://www.iso.org/standard/44373.html
- ISO. ISO/IEC 27001:2022. https://www.iso.org/standard/27001
- NIST. SP 800 82 Rev 3. https://csrc.nist.gov/pubs/sp/800/82/r3/final
- Android Developers. Cryptography. https://developer.android.com/privacy-and-security/cryptography
- Android Developers. Android Keystore system. https://developer.android.com/privacy-and-security/keystore
- RFC Editor. RFC 5869 HKDF. https://www.rfc-editor.org/rfc/rfc5869
