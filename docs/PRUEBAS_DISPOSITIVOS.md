# Prueba de interconexión en dos teléfonos Android

## Preparación

Usa dos teléfonos físicos con el APK de la misma versión. Crea e inicia una cuenta en cada uno, activa fecha y hora automáticas, mantén la app en primer plano y conecta ambos a una red Wi-Fi WPA2 o WPA3 sin aislamiento entre clientes. Un tercer teléfono puede proveer el hotspot. No conectes la prueba a una red OT de producción.

Anota fecha, modelo, versión Android, tipo de red y distancia aproximada. No incluyas contraseñas, el código de emparejamiento ni identificadores de otras personas en las evidencias.

## Casos y criterios de aceptación

| Caso | Procedimiento | Resultado esperado | Evidencia |
| --- | --- | --- | --- |
| T01 Login válido | Ingresar usuario y clave configurados | Acceso a Conexión | Foto tomada por un tercer dispositivo sin credenciales |
| T02 Login inválido | Fallar cinco veces y luego usar clave correcta | Bloqueo temporal de 30 s | Mensaje de bloqueo; no publicar clave |
| T03 Conexión base | B Equipo, A Operador, IP y código vigentes | Sesión autenticada y muestras periódicas | Captura de Monitoreo en ambos |
| T04 Alarma | En B simular 10 °C y puerta abierta | A muestra alerta y estado de puerta | Capturas de ambos y bitácora |
| T05 Orden AUTO | En A enviar consigna 4.5 °C en AUTO | ACK y cambio visible en B | Captura del ACK y estado de B |
| T06 Orden MANUAL | En A activar compresor y confirmar | B aplica MANUAL y compresor ON | Capturas y bitácora |
| T07 Solo lectura | Detener B, quitar Permitir control, reiniciar y emparejar con nuevo código | Monitoreo funciona, controles remotos bloqueados | Pantalla Activador sin permiso |
| T08 Código errado | Ingresar un código diferente | Sin sesión ni datos del equipo | Estado de conexión |
| T09 Corte de red | Apagar Wi-Fi de A por 15 s durante MANUAL | Datos vencidos, controles bloqueados, B retorna a AUTO 4 °C al expirar enlace | Video corto y bitácora de B |
| T10 Recuperación | Restaurar Wi-Fi y pulsar Conectar con código vigente | Nueva sesión, sin reenvío de orden anterior | Bitácoras antes y después |
| T11 Estabilidad cercana | Mantener conexión 10 min, enviar 10 órdenes separadas al menos 5 s | Sin cierre inesperado; 10 ACK; telemetría sin interrupciones >3 s | Tabla de mediciones y bitácora |
| T12 Mayor distancia | Repetir 5 min a 8 a 10 m y con una pared si el lugar lo permite | Medir deterioro y reportarlo, sin ocultar fallos | Tabla con distancia real |
| T13 Hotspot | Repetir 5 min en punto de acceso de un tercer teléfono | Sesión funcional, medir RTT y ACK | Tabla y bitácora |
| T14 Segundo plano | Cambiar a otra app | Cierre de enlace y reconexión manual necesaria | Mensaje y bitácora |
| T15 Sesión inactiva | Dejar la app abierta 5 min sin tocar | Vuelve a Login y cierra enlace | Foto externa del Login |

Los límites de aceptación de la prueba son propuestos para la evaluación, no resultados ya medidos. La app recibe aproximadamente una muestra por segundo y marca datos vencidos después de 3 s. El timeout de recepción es 7 s; agrega margen de observación para que el cierre de ambos extremos sea visible. La aplicación no promete una latencia de red fija.

Durante los ensayos T11 a T13, toca una zona de cada app o su navegación al menos cada tres minutos para mantener ambas sesiones activas. El cierre por cinco minutos de inactividad es deliberado y se verifica por separado en T15. Al cerrar sesión o detener el Equipo se detiene la simulación; el control de respaldo se mantiene mientras la ejecución del Equipo continúa iniciada en primer plano.

## Medir estabilidad y eficiencia

En Registro se muestran RTT actual por ping, muestras recibidas, saltos del contador, intervalo entre las dos últimas muestras, órdenes enviadas y confirmadas y bytes cifrados de la sesión. El contador de saltos detecta muestras no observadas; no equivale a pérdida de paquetes IP porque TCP retransmite y entrega en orden.

Para cada escenario anota 10 lecturas de RTT tomadas con separación de 5 s y calcula media, mínimo y máximo. Para evaluar p95 se requiere una serie más larga y un cálculo adicional; la app no muestra ese percentil directamente. Registra las órdenes y el estado antes de desconectar o exportar, pues los contadores de tráfico están asociados a la sesión activa.

Calcula éxito de órdenes = confirmadas / enviadas × 100. Para muestras usa saltos / (muestras + saltos) × 100, reportando que se trata de saltos observados, con el intervalo inicial y las reconexiones excluidos. No compares milisegundos del ensayo loopback del computador con las medidas Wi-Fi de teléfonos.

Los contadores de órdenes son acumulativos durante la ejecución de la app. Anota su valor al inicio y al final de cada escenario y usa la diferencia. Las muestras y el tráfico cifrado corresponden a la sesión del enlace; mide cada escenario dentro de una conexión continua.

Completa `evidencias/Registro_pruebas_wifi.csv`, guarda las bitácoras y tus capturas y resume los resultados reales en el informe. Si un caso falla, anota causa, ajuste y nueva prueba. No marques como aprobado un caso que no se ejecutó.

## Demostración de 6 a 8 minutos

1. Explica la pérdida de visibilidad de temperatura en una cámara de refrigeración y por qué se conecta el sensor con un operador.
2. Muestra las cuentas locales y los dos roles Android. No reveles las contraseñas.
3. Establece la conexión y muestra telemetría de B en A.
4. Provoca una alarma en B y muestra su recepción en A.
5. Envía una orden desde Activador; muestra ACK y cambio en B.
6. Corta Wi-Fi y explica el modo automático local de respaldo; reconecta.
7. Muestra solo monitoreo, la bitácora y las métricas de estabilidad de tus ensayos.
8. Señala el código en GitHub, los controles de seguridad y sus límites académicos.
