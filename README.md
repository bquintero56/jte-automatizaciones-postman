# Pruebas de endpoints en Jenkins (JTE)

Pipeline de Jenkins Templating Engine que llama a una lista de endpoints HTTP, comprueba que respondan el código esperado
(200 por defecto) y envía un correo HTML con el resultado de cada uno: `OK` o `ERROR`.

Un solo repositorio con tres carpetas, una por pieza de JTE. En Jenkins apunta cada fuente a este repo con su **Base Directory**:

| Fuente JTE | Base Directory |
|---|---|
| Pipeline Configuration / Templates | `jte-pipeline-configurations-automations` y `jte-pipelines-template` |
| Library Source | `jte-automations-pipeline-libraries` |

```
jte-pipeline-configurations-automations/
└── probar-endpoints/pipeline_config.groovy        endpoints, hora, destinatarios

jte-automations-pipeline-libraries/
├── pruebas_endpoints/                             cron + curl a cada endpoint
└── notificacion_correo/                           correo con la tabla de resultados (con CC)

jte-pipelines-template/
└── probar-endpoints/probar_endpoints.groovy
```

## Antes de ejecutar

1. En `pipeline_config.groovy`: los endpoints reales, `hora`, `zonaHoraria`, destinatarios/copia, `pipeline_template` y
   `jenkins_agent_label`.
2. El nodo de `jenkins_agent_label` necesita `curl` y salida de red hacia los endpoints.
3. Plugin Email Extension con SMTP configurado.
4. Commit y push, y lanza el job una vez a mano: registra el cron.

## OAuth 2.0

Si un endpoint necesita token (como *Get New Access Token* en Postman):

1. En Jenkins crea una credencial **Username with password**: usuario = Client ID, contraseña = Client Secret.
2. En la config agrega una entrada en `autenticaciones` (`nombre`, `tokenUrl`, `scope`, `credentialsId`, `grantType`).
3. En el endpoint pon `auth: '<nombre>'`: se envía `Authorization: Bearer <token>`.

## Proxy

Si el nodo necesita proxy para llegar a las URLs, pon `proxy = 'http://host:puerto'` en la config. Si el proxy pide usuario, crea una
credencial **Username with password** y pon su ID en `proxyCredentialsId`. Un endpoint o autenticación con `usarProxy: false` sale directo.

**El repo es público:** no pongas tokens ni contraseñas en la config; usa credenciales de Jenkins.
