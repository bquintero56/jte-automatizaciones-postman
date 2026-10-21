jte {
    allow_scm_jenkinsfile = false
    pipeline_template = 'probar-endpoints/probar_endpoints.groovy'   // AJUSTAR a la ruta real de tu template
}

// El template usa execution.time, execution.units y jenkins_agent_label
keywords {
    execution {
        time  = 15
        units = 'MINUTES'
    }
    jenkins_agent_label = 'linux-pro'                                 // AJUSTAR: nodo con curl y salida a los endpoints
}

libraries {
    pruebas_endpoints {
        zonaHoraria     = 'America/Santiago'     // cron y fecha del correo
        hora            = '08:00'                // AJUSTAR: ejecución diaria (HH:mm en zonaHoraria)
        codigoEsperado  = 200                    // código HTTP que se considera OK (cada endpoint puede sobrescribirlo)
        timeoutSegundos = 30                     // tiempo máximo por petición; si se supera, el código es 000 (ERROR)
        estadoSiFallan  = 'UNSTABLE'             // SUCCESS, UNSTABLE o FAILURE: color del build si algún endpoint falla

        // Proxy para el token y los endpoints. Usuario/clave del proxy NO van aquí: credencial Jenkins "Username with password".
        // Un endpoint o autenticación con usarProxy: false sale directo (sin proxy).
        proxy              = 'http://proxy.empresa.com:8080'   // AJUSTAR; '' = sin proxy
        proxyCredentialsId = ''                                // AJUSTAR si el proxy pide usuario (ej. 'proxy-corporativo'); '' = sin usuario

        // OAuth 2.0 (lo mismo que "Configure New Token" en Postman). Client ID y Client Secret NO van aquí (repo público):
        // van en una credencial de Jenkins "Username with password" (usuario = Client ID, contraseña = Client Secret).
        // Campos: nombre, tokenUrl, credentialsId (obligatorios); scope, grantType ('client_credentials' por defecto), usarProxy
        autenticaciones = [
            [nombre: 'api-360', tokenUrl: 'https://auth.empresa.com/oauth2/token', scope: 'api.read',     // AJUSTAR
             credentialsId: 'oauth-api-360', grantType: 'client_credentials']
        ]

        // AJUSTAR: endpoints reales. Campos: nombre, url (obligatorios); metodo (GET por defecto), esperado, headers [:], body,
        // auth (nombre de una entrada de autenticaciones: envía "Authorization: Bearer <token>"), usarProxy (false = sin proxy)
        endpoints = [
            [nombre: 'Health API',       url: 'https://api.empresa.com/health'],
            [nombre: 'Listar clientes',  url: 'https://api.empresa.com/v1/clientes', metodo: 'GET', auth: 'api-360',
             headers: ['Accept': 'application/json']],
            [nombre: 'Crear cotización', url: 'https://api.empresa.com/v1/cotizaciones', metodo: 'POST', esperado: 201,
             headers: ['Content-Type': 'application/json'], body: '{"producto": "AUTO", "monto": 1000}']
        ]
    }

    notificacion_correo {
        destinatarios = 'persona1@empresa.com,persona2@empresa.com'
        copia         = ''                                            // CC separados por coma (opcional; '' = sin copia)
        asuntoPrefijo = 'Monitoreo endpoints'
        asuntoSufijo  = '360'
    }
}
