# Autenticación y datos persistentes en SQLite

## Elección del almacenamiento

La guía sumativa permite seleccionar SQLite local, Firebase o una API con MySQL. FríoSeguro OT elige SQLite para autenticar sin conexión y conservar lecturas en una LAN sin Internet. La evaluación puede mostrar el ciclo completo de captura, almacenamiento, consulta y exportación usando esta opción.

| Alternativa | Seguridad y operación | Escalabilidad | Implementación y decisión |
| --- | --- | --- | --- |
| SQLite local | Sandbox Android; credenciales y payloads cifrados con Keystore; funciona sin Internet | Una cuenta y hasta 10.000 lecturas por instalación; sin sincronización central | Seleccionada: APIs nativas, transacciones y consultas locales demostrables |
| Firebase | Requiere proyecto, reglas, autenticación y conexión con servicios externos | Gestión centralizada y sincronización entre clientes | Adecuada para ampliar el proyecto a varias ubicaciones con nube |
| API con MySQL | Requiere backend HTTPS, validación de permisos y gestión de sesiones en servidor | Servicio central y consultas de mayor volumen | Adecuada cuando se dispone de servidor; Android se conecta a una API, no directamente a MySQL |

## Esquema

La base privada se llama `frioseguro.db`, versión de esquema 1. SQLite utiliza WAL. `LocalDatabase` comparte una conexión administrada por proceso y no solicita permisos de almacenamiento externo.

```sql
CREATE TABLE account (
  id INTEGER PRIMARY KEY CHECK(id=1),
  vault TEXT NOT NULL,
  failures INTEGER NOT NULL DEFAULT 0,
  blocked_until INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE telemetry (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  captured_at INTEGER NOT NULL,
  received_at INTEGER NOT NULL,
  source TEXT NOT NULL CHECK(source IN ('LOCAL','REMOTO')),
  alarm INTEGER NOT NULL CHECK(alarm IN (0,1)),
  payload BLOB NOT NULL
);
CREATE INDEX telemetry_time ON telemetry(captured_at DESC,id DESC);
CREATE INDEX telemetry_filter ON telemetry(source,alarm,captured_at DESC,id DESC);
```

`vault` contiene un sobre AES 256 GCM codificado en Base64. Dentro se guardan usuario, sal y hash PBKDF2 SHA256 de 210.000 iteraciones. La contraseña no se almacena. La clave AES queda en Android Keystore y no se exporta. El contador de fallos y el bloqueo de 30 segundos persisten en SQLite. El cierre de sesión no borra cuenta ni historial.

Si la instalación conserva la firma de la versión 1.0, `AuthStore` migra su sobre cifrado desde las preferencias antiguas sin cambiar clave, usuario, hash ni bloqueo. Solo retira la copia anterior cuando confirma la cuenta guardada en SQLite. La desinstalación elimina los datos y las claves de esa instalación.

## Lecturas

El Equipo registra una lectura LOCAL por ciclo. El Operador registra cada telemetría REMOTA aceptada. El payload JSON versión 2 contiene temperatura Celsius, consigna, modo, compresor, puerta, alarma y número de muestra. `captured_at` y `received_at` son milisegundos Unix; los tiempos de recepción del enlace para RTT y vigencia usan un reloj monotónico aparte.

El payload se cifra con AES GCM y un IV aleatorio por registro. La hora de captura, recepción, origen y alarma se autentican como datos asociados. Esos metadatos permanecen visibles en la base para indexar y filtrar. La base completa no usa SQLCipher. Un cambio en los metadatos protegidos o en el ciphertext impide descifrar el registro.

Cada guardado es transaccional. Se retiran los IDs más antiguos al superar 10.000 lecturas. Los registros se conservan al cerrar sesión, desconectar y reiniciar. La cola de persistencia admite 64 operaciones; un fallo o saturación incrementa el contador de errores y se informa en Registro. Las tareas de disco y criptografía de captura no bloquean el hilo de red o la UI.

## Consultas y CSV

Historial ofrece filtros de origen, periodo y alarma. Las condiciones se construyen con campos permitidos y argumentos enlazados; los valores de usuario no se concatenan al SQL. La consulta obtiene conteos y filas en una transacción coherente y descifra después de liberar el bloqueo.

```sql
SELECT id,captured_at,received_at,source,alarm,payload
FROM telemetry
WHERE source=? AND alarm=? AND captured_at>=?
ORDER BY captured_at DESC,id DESC
LIMIT 50;
```

La UI muestra las 50 coincidencias más recientes y refresca cada cinco segundos. El CSV exporta todas las coincidencias conservadas, con orden cronológico y hasta 10.000 filas, además de su encabezado. Usa UTF-8, coma, terminadores CRLF, punto decimal y fechas UTC ISO 8601. Los booleanos se expresan como 0/1. Campos:

`schema_version,captured_at_utc,received_at_utc,source,sample,temperature_c,setpoint_c,mode,compressor_on,door_open,alarm`

Los datos JSON y el CSV documentado permiten importar lecturas en una hoja de cálculo, una base relacional o una herramienta de análisis sin exponer claves ni credenciales. La fuente de almacenamiento implementada y demostrada es SQLite. La consulta local continúa disponible sin red; la actualización en tiempo real procede del Equipo enlazado por TLS.

## Comprobaciones

`AccountSecurityTest` verifica cuenta cifrada, bloqueo y migración. `HistoryStorageTest` cubre persistencia, filtros, CSV, rechazo de entradas inválidas, alteración de metadatos y retención de 10.000 filas. La persistencia entre procesos se verifica ejecutando `RestartPersistenceTest#seed`, deteniendo el proceso y ejecutando `RestartPersistenceTest#verify` en otra instrumentación. Consultar el resultado realmente obtenido en `evidencias/automatizadas/VERIFICACION.md`.
