# Relación con la pauta de evaluación

La pauta de la imagen suma 100 puntos. Esta matriz localiza las funcionalidades y define la evidencia necesaria para revisar cada criterio. El puntaje lo asigna el docente al verificar el proyecto y la demostración.

| Criterio de la imagen | Puntos | Implementación | Evidencia para mostrar |
| --- | ---: | --- | --- |
| 2.1.1.1 Login con usuario y contraseña | 10 | MainActivity, AuthStore y SQLite: cuenta cifrada, ingreso, migración y bloqueo persistente | Acceso correcto e incorrecto en Android |
| 2.1.1.2 Monitoreo y Activador con Material Design | 10 | Pantallas con TextInputLayout, botones Material, slider, radio buttons, switch, tarjetas, navegación y gráfico | Lectura remota y una orden confirmada |
| 2.1.2.3 Conexión inalámbrica eficiente y segura | 15 | WifiLink, TlsTransport y SecureChannel: Wi-Fi LAN, TLS 1.2/1.3 con huella, PSK, límites y métricas | Dos Android en Wi-Fi, bitácora y pruebas de cifrado |
| 2.1.2.4 Solución innovadora y efectiva | 10 | Cámara simulada, alarma por temperatura o puerta, control con histéresis, fallback local, confirmación e historial SQLite | Provocar evento en B y atenderlo desde A |
| 2.1.3.5 Medidas de seguridad basadas en ISO | 10 | Credenciales protegidas, autorización, privacidad, integridad y sesión; relación documentada con ISO 27400 y 27001 | Seguridad.md y pruebas negativas |
| 2.1.3.6 Verificar y documentar lineamientos OT | 10 | Matriz de riesgo, control local de respaldo, separación de red propuesta, límites y guía de validación | Informe y caso de corte Wi-Fi |
| 2.1.4.7 Configurar y probar interconexión móvil | 15 | Misma APK en roles Equipo y Operador; protocolo bidireccional | Ejecutar T03 a T10 en dos teléfonos físicos |
| 2.1.4.8 Estabilidad y eficiencia en escenarios | 20 | RTT, muestras, saltos, intervalo, bytes, ACK y bitácora; protocolo T11 a T15 | Medidas reales de red cercana, distancia y hotspot |
| Total | 100 | Ocho criterios cubiertos en el diseño | Revisión y calificación docente |

Los ensayos del computador y del emulador deben identificarse como tales. Los criterios de interconexión inalámbrica y estabilidad requieren completar el registro con tus teléfonos y demostrarlo al docente. Una tabla vacía no acredita esos ensayos.

## Requisitos añadidos por la guía sumativa

La guía TI3042 U2 ES señala evaluación individual de 25 % y seis semanas. SQLite, Firebase y API con MySQL se presentan como alternativas; se implementa SQLite y se demuestra el ciclo de almacenamiento y consulta mediante esa opción.

| Requisito | Implementación o documento | Comprobación |
| --- | --- | --- |
| Seleccionar y justificar herramientas Android | Kotlin, Android Studio, Material y SQLite; DISENO.md y DATOS_SQLITE.md | Comparación de seguridad, escalabilidad y facilidad |
| Login con almacenamiento y validación correctos | AuthStore, LocalDatabase y DeviceVault | Cuenta cifrada, bloqueo persistente y migración |
| Capturar y conservar datos de sensores | HistoryStore y TelemetryCodec | Lecturas LOCAL/REMOTO cifradas y hasta 10.000 filas |
| Consultar datos de la opción seleccionada | Historial: origen, periodo y alarma | Consulta offline, conteos y exportación CSV de coincidencias |
| Interoperabilidad y actualización en tiempo real | JSON esquema 2 y CSV documentado; captura cada segundo | DATOS_SQLITE.md, HistoryStorageTest e integración local |
| Justificar distancia, consumo y estabilidad | Wi-Fi TLS sobre TCP; DISENO.md | Alcance y consumo físicos deben medirse |
| Conexión segura HTTPS o TLS | TLS real antes de autenticar PSK; sin fallback en claro | TLS 1.2 y 1.3 JVM y Android Keystore |
| Diseño de pantallas y autenticación | Figuras de seis pantallas e Informe_FrioSeguro_OT.docx | Validación del docente pendiente |
| Demostrar autenticación, enlace, datos y seguridad | Guion de ocho a diez minutos y T01 a T23 | Demostración física y registro pendientes |
| Entrega por AAI | TXT con enlace real al repositorio público | Subir y confirmar Enviar tarea; no correo |

Los resultados de cada prueba automatizada se describen en evidencias/automatizadas/VERIFICACION.md. La aprobación docente, continuidad con Unidad 1 y resultados de dos teléfonos no se dan por realizados.
