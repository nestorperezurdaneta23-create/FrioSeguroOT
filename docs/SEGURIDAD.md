# Seguridad de FríoSeguro OT 1.1

## Activos y modelo de riesgo

Se protegen la cuenta local, el código FS2, los valores de sensores guardados, la telemetría y las órdenes. Se contemplan observación o manipulación de tráfico LAN, sustitución de identidad, códigos equivocados, repetición, consulta no autorizada y pérdida de enlace o almacenamiento.

Cada teléfono conserva una cuenta local. El Equipo concede control o solo lectura a quien pruebe la PSK vigente. La autenticación local y ese emparejamiento no equivalen a una identidad centralizada de una planta. El código completo se transfiere presencialmente o por un medio confiable.

## Controles y verificación

| Objetivo | Control implementado | Comprobación |
| --- | --- | --- |
| Autenticación local | Cuenta SQLite; contraseña 12 a 128 caracteres con letras y números; PBKDF2 HMAC SHA256, 210.000 iteraciones y sal aleatoria de 16 B | Acceso correcto, clave incorrecta y clave débil |
| Credenciales en reposo | Usuario, sal y hash dentro de un sobre AES 256 GCM; clave Android Keystore no exportable | Persistencia cifrada y migración del registro 1.0 |
| Intentos fallidos | Cinco fallos bloquean durante 30 s; contador y bloqueo persistidos en SQLite | Bloqueo al recrear AuthStore |
| Transporte | SSLSocket y SSLServerSocket; TLS 1.2/1.3; suites AEAD; sin fallback a TCP sin TLS | Intercambio real y protocolos habilitados |
| Identidad del Equipo | Huella SHA256 transferida en FS2; coincidencia exacta, certificado vigente y firma propia comprobados | Pin incorrecto, certificado vencido y cadena inesperada rechazados |
| Permiso del Operador | PSK aleatoria de 128 bits por inicio, nonces y HMAC enlazados con el permiso concedido | Código erróneo y sesión de solo monitoreo |
| Integridad y repetición | Tramas AES GCM dentro de TLS; claves por dirección, secuencia estricta y tamaño máximo 16.384 B | Alteración, replay y exceso de tamaño rechazados |
| Órdenes | UUID, vigencia, límites, permiso en el receptor y una orden pendiente; ACK sin reenvío automático | Pruebas de modelo y demostración bidireccional |
| Sensores en reposo | Payload AES GCM; horas, origen y alarma autenticados como AAD | Persistencia y alteración de metadatos SQLite |
| Consulta | Parámetros enlazados, campos permitidos, límites y transacciones | Filtros combinados, entradas inválidas y exportación coherente |
| Recursos | 10.000 lecturas, colas acotadas, timeouts y reporte de errores de almacenamiento | Prueba del límite y métricas visibles |
| Continuidad OT | Fallback AUTO 4 °C al perder enlace mientras el Equipo permanece iniciado; control remoto bloqueado con datos vencidos | Caso de corte de red y pruebas de fallback |
| Sesión y privacidad | Inactividad 5 min, cierre de enlace en segundo plano, backup excluido y ventanas sensibles protegidas | Ensayos de sesión y revisión de manifiesto |

## TLS en una LAN sin autoridad certificadora pública

El Equipo genera una clave RSA de 2048 bits en Android Keystore y un certificado propio. El Operador verifica la huella SHA256 exacta recibida por un medio de confianza, su vigencia y su firma. Nunca aprende el pin de la conexión que está intentando validar ni acepta cualquier certificado. TLS usa intercambio efímero y AEAD. La PSK aporta autenticación del Operador y permiso de aplicación dentro de ese transporte.

Android desaconseja el pinning estático de servicios remotos cuando un cambio de certificado puede dejar clientes sin acceso. Aquí se empareja directamente un Equipo controlado por el estudiante y se entrega su identidad vigente en cada código FS2; al rotar el certificado se vuelve a emparejar. El procedimiento depende de que esa transferencia sea confiable. No usa una identidad DNS de servidor público.

La PSK, de 32 caracteres hexadecimales, y el pin, de 64, se codifican como `FS2.SECRETO.HUELLA`. El código no se guarda en la base ni en bitácoras. Las cadenas en RAM no ofrecen borrado garantizado. Copiarlo al portapapeles es una acción explícita del usuario; en Android 13 o posterior se marca como contenido sensible.

## SQLite y privacidad

El archivo de base de datos reside en el espacio privado de Android. El payload de sensores y la cuenta están cifrados antes del guardado; los metadatos de tiempo, origen y alarma quedan disponibles para índices. No se afirma cifrado de toda la base. Las claves AES permanecen en Keystore, sujetas a las capacidades del dispositivo.

El CSV contiene las lecturas seleccionadas y ningún secreto de acceso. Al exportarlo, el estudiante decide dónde guardarlo y debe compartirlo solo con el destinatario adecuado. La bitácora de eventos es temporal; el historial de sensores es persistente. Ninguno constituye un registro inmutable frente a un dispositivo comprometido o eliminación de filas.

## Estándares y entorno OT

Los controles se relacionan con objetivos de ISO/IEC 27400:2022 e ISO/IEC 27001:2022: control de acceso, confidencialidad, integridad, disponibilidad, privacidad y gestión de riesgos. No se declara certificación ni cumplimiento de todas las cláusulas. NIST SP 800-82 Rev.3 orienta la consideración conjunta de rendimiento, confiabilidad y seguridad del proceso OT.

La demostración debe usar WPA2/WPA3, sin exponer el puerto 8842 a Internet y separada de redes OT de producción. Son configuraciones del entorno. Los umbrales térmicos son didácticos. La app no garantiza seguridad sanitaria ni sustituye un PLC, watchdog o control físico independiente.

Un dispositivo rooteado, malware con control del proceso, filtración del código o denegación de servicio pueden superar el modelo. Antes de integrar equipamiento real se requieren revisión independiente del diseño, validación del proceso, administración de identidades, auditoría protegida y un controlador seguro fuera de la app.

## Fuentes

- ISO/IEC 27400:2022. https://www.iso.org/standard/44373.html
- ISO/IEC 27001:2022. https://www.iso.org/standard/27001
- NIST SP 800-82 Rev.3. https://csrc.nist.gov/pubs/sp/800/82/r3/final
- Android Developers. SQLite. https://developer.android.com/training/data-storage/sqlite
- Android Developers. Security with network protocols. https://developer.android.com/privacy-and-security/security-ssl
- Android Developers. Keystore. https://developer.android.com/privacy-and-security/keystore
- RFC 5869, HKDF. https://www.rfc-editor.org/rfc/rfc5869
