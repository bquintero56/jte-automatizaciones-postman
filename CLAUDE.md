# Contexto del proyecto: pruebas de endpoints en Jenkins con JTE

Idioma de trabajo: **español** (comentarios, logs, mensajes de error y correos).

## 1. Qué es esto

Pipeline de **monitoreo de endpoints** que corre en Jenkins con **Jenkins Templating Engine (JTE)**. Llama a una lista de endpoints HTTP
con `curl` desde el nodo de Jenkins, compara el código de respuesta con el esperado (**200** por defecto) y envía **un correo HTML** con el
resultado: `OK` si coincide, `ERROR` si responde otro código o no responde.

- **Pipeline – `probar-endpoints`**: prueba todos los endpoints de la config en cada ejecución (cron diario o *Build* manual).
- Se esperan **bastantes pruebas**: la lista de endpoints vive en la config y crece sin tocar código.

Regla general: **un endpoint con error NO detiene el resto ni impide el correo**. El color del build lo decide `estadoSiFallan`.

**Repo hermano (referencia):** `jte-automatizaciones` (https://github.com/bquintero56/jte-automatizaciones), en
`C:\Users\bquin\Desktop\jte\jte-automatizaciones\jte-automatizaciones`. Tiene dos pipelines de validación de archivos por SSH ya en
producción con este mismo estándar; su CLAUDE.md documenta decisiones y código probado (SSH + sudo, `buscar()`, adjuntos en el correo,
CC, cron con zona horaria). Reutilizar de ahí lo que sirva en lugar de reinventarlo.

## 2. Arquitectura JTE (1 repo, 3 carpetas)

Todo vive en el repo `jte-automatizaciones-postman`; en Jenkins cada fuente JTE apunta a su carpeta con *Base Directory*.

| Carpeta | Qué contiene | Quién lo usa |
|---|---|---|
| `jte-pipeline-configurations-automations` | Una carpeta por automatización con su `pipeline_config.groovy`: **solo valores** (endpoints, hora, destinatarios). Sin lógica. | Todos los steps vía `config` |
| `jte-automations-pipeline-libraries` | Una carpeta por librería con `library_config.groovy` (valida los campos) y `steps/*.groovy` (la **lógica**). | El template |
| `jte-pipelines-template` | Una carpeta por automatización con el template `.groovy` que solo **orquesta**: llama a los steps en orden. | Jenkins |

### Cómo se enlazan las piezas

- El **nombre del bloque** en `pipeline_config.groovy` = **nombre de la carpeta** de la librería (`pruebas_endpoints`, `notificacion_correo`).
  Si difiere una letra, JTE no la carga.
- El **nombre del archivo** de un step es el nombre con que se llama en el template (`probar_endpoints.groovy` → `probar_endpoints()`).
- Dentro de un step, `config` es el bloque de configuración de **esa** librería (`config.endpoints`, `config.destinatarios`...).
- `execution.time`, `execution.units` y `jenkins_agent_label` vienen de la sección `keywords` de la configuración.
- **Todo campo de la config debe estar declarado en `library_config.groovy`** o JTE falla con `field 'x' is not used`
  (los opcionales en un bloque `optional { }`).

### Estructura de directorios

```
jte-pipeline-configurations-automations/
└── probar-endpoints/pipeline_config.groovy

jte-automations-pipeline-libraries/
├── pruebas_endpoints/
│   ├── library_config.groovy
│   └── steps/
│       ├── programar_ejecuciones.groovy      cron diario (config.hora + zonaHoraria)
│       ├── obtener_tokens_oauth.groovy       token OAuth 2.0 por cada entrada de config.autenticaciones
│       └── probar_endpoints.groovy           curl a cada endpoint (con Bearer si tiene auth) y comparación del código
└── notificacion_correo/
    ├── library_config.groovy                 destinatarios, copia (opcional), asunto
    └── steps/enviar_correo_endpoints.groovy  correo con la tabla de resultados

jte-pipelines-template/
└── probar-endpoints/probar_endpoints.groovy
```

## 3. Convenciones técnicas (respetarlas al modificar)

1. **Nada quemado en los steps.** Todo valor sale de `config`. Un campo nuevo se agrega en la config **y** en `library_config.groovy`.
2. **HTTP con `curl` en el nodo:** `curl -s -o /dev/null -w '%{http_code} %{time_total}' -X <metodo> [-H ...] [--data-binary @archivo] --max-time <timeout> '<url>' || true`.
   - Sin respuesta (DNS, conexión rechazada, timeout) → `curl` imprime código `000` → `ERROR` con detalle "Sin respuesta".
   - El `body` se escribe a un archivo (`writeFile`) y se envía con `--data-binary @archivo` para no romper comillas del JSON.
3. **Log limpio.** Los `sh` usan `"""#!/bin/sh` pegado a las comillas (sin el shebang Jenkins corre `sh -x` y el log se llena de trazas).
   Las continuaciones de línea dentro de ese bloque son `\\`.
4. **Fechas con `date` en el nodo**, con la zona horaria de la config (`TZ='<zonaHoraria>' date +'<formato>'`), no en Groovy.
5. **Estados, no excepciones.** `probar_endpoints()` devuelve la lista de resultados. `estadoSiFallan` (`SUCCESS` | `UNSTABLE` | `FAILURE`)
   decide el color del build con `unstable(...)` o `currentBuild.result = 'FAILURE'`, **nunca con `error`**, para que el correo se envíe.
6. **Estructura del template:** `timeout(execution)` → `node(jenkins_agent_label)` → `withFolderProperties` → `try/catch/finally`, con
   `clean_job_workspace()` en el `finally`. Cada tarea es un `stage`. Todo `catch (Exception e)` va precedido de
   `catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) { throw e }` (aborto o timeout → ABORTED).
7. **CPS de Jenkins:** preferir `for` clásico a `eachWithIndex` y a `Map.collect { k, v -> }`; `findAll {}.size()` en vez de `count {}`.
8. **Correo** con `emailext` (Email Extension), `mimeType: 'text/html'`.
   - `to` = `destinatarios` + cada dirección de `copia` con prefijo `cc:` (separadas por coma en la config).
   - Asunto: `<asuntoPrefijo> <asuntoSufijo> - <N con error | todo OK> - <fecha>`.
   - Cuerpo: `Estimados buenos días,` → resumen (N probados, X OK, Y con error) → tabla → `Saludos.`
   - Tabla `ENDPOINT | MÉTODO | URL | ESPERADO | CÓDIGO | TIEMPO | ESTATUS`, `OK` en verde y `ERROR` en rojo.

## 4. Pipeline – `probar-endpoints`

- **Config (`pruebas_endpoints`):** `zonaHoraria`, `hora` (HH:mm), `codigoEsperado` (200), `timeoutSegundos`, `estadoSiFallan`,
  `endpoints` (lista). Cada endpoint: `nombre`, `url` (obligatorios); `metodo` (GET por defecto), `esperado` (sobrescribe
  `codigoEsperado`, ej. 201 en un POST), `headers` (mapa), `body` (texto), `auth` (nombre de una entrada de `autenticaciones`).
- **OAuth 2.0 (`autenticaciones`, opcional):** lista con `nombre`, `tokenUrl`, `credentialsId` (obligatorios), `scope`, `grantType`
  (`client_credentials` por defecto). Equivale a *Configure New Token → Get New Access Token* de Postman con *Client Authentication =
  Send as Basic Auth header*. El Client ID/Secret viven en una credencial Jenkins **Username with password** (usuario = Client ID,
  contraseña = Client Secret). *Token Name* de Postman = `nombre`.
- **Step `obtener_tokens_oauth()`** (stage `OAuth: obtener tokens`): por cada autenticación hace `POST tokenUrl` con `curl -u` +
  `--data-urlencode grant_type/scope`, extrae `access_token` con `sed` (sin plugins) y lo deja en `oauth_token_<n>.txt` del workspace.
  El token **nunca** pasa por Groovy ni por el log: el `sh` del endpoint lo lee con `$(cat archivo)`. Devuelve
  `nombre → [archivo, ok, codigo, detalle]`, que se pasa a `probar_endpoints(tokens)`. Si no hay token, los endpoints con ese `auth`
  no se llaman y quedan `ERROR` (código `000`) con el motivo en `detalle`.
- **Ejecución:** `programar_ejecuciones()` registra el cron diario a `hora` en `zonaHoraria`; cada ejecución prueba **todos** los endpoints.
- **Step `probar_endpoints()`**: por endpoint devuelve `[nombre, metodo, url, esperado, codigo, tiempoMs, estatus, detalle, fecha]`.
- **Correo (`enviar_correo_endpoints`)**: se envía **siempre**.

## 5. Puntos a confirmar / pendientes

1. **Autenticación:** OAuth 2.0 resuelto (`autenticaciones` + `obtener_tokens_oauth`). Confirmar que el *Grant Type* real sea
   `client_credentials`; si es otro (password, authorization code) hay que ampliar el step. Nunca secretos en la config (repo **público**).
2. **¿Colecciones de Postman?** Si las pruebas ya existen como colección de Postman, se puede ejecutar con **Newman** en el nodo en vez de
   mantener la lista en la config (requiere Node.js + `newman` en el agente). Decidir.
3. **Validaciones extra:** hoy solo se compara el código HTTP. Posibles: tiempo máximo de respuesta, contenido del cuerpo, certificado.
4. **Certificados autofirmados:** si algún endpoint los usa, `curl` falla (`000`); habría que permitir `-k` por endpoint (opcional).
5. **Muchos endpoints:** si la lista es grande, agrupar por sistema en el correo o probar en paralelo (`parallel`).
6. `pipeline_template` y `jenkins_agent_label` de la config están con valores de ejemplo: ajustar a la instalación real.

## 6. Cómo probar

- Ajustar `endpoints` (o probar con `https://httpbin.org/status/200` y `https://httpbin.org/status/500` si el nodo tiene salida a internet).
- Commit y push, y lanzar el job una vez a mano (*Build*): registra el cron.
- Probar a mano desde el nodo: `curl -s -o /dev/null -w '%{http_code}\n' '<url>'`.
- `field '...' is not used` → falta declarar el campo en `library_config.groovy`. `No such DSL method` → falta la librería en la config.
- Si Jenkins pide aprobaciones de script (*In-process Script Approval*), aprobarlas y relanzar.

## 7. Cómo trabajar en este repo (instrucciones para Claude Code)

- Mantén la separación: **config = valores, librería = lógica, template = orquestación**.
- Antes de agregar un campo, añádelo a la config **y** a `library_config.groovy`.
- No pongas valores literales (URLs, tokens, horas, formatos) dentro de los steps. **Nunca** secretos en el repo: es público.
- Conserva el formato de log con ✅ / ❌ y el resumen final.
- Si cambias el contrato de datos (claves del mapa que devuelve `probar_endpoints`), actualiza también el step de correo y este documento.
- No hay Jenkins en el entorno de desarrollo: revisa la sintaxis con cuidado y explica cómo probar cada cambio.
- **Flujo de trabajo pedido por el usuario:** después de cada cambio, **commit y push** a `main`
  (https://github.com/bquintero56/jte-automatizaciones-postman, autor `bquintero <bquintero56@gmail.com>`) y **decir qué archivos se
  crearon, modificaron o borraron**, con lo que cambió en cada uno, para que pueda replicarlos.
