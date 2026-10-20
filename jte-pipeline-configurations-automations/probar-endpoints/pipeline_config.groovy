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

        // AJUSTAR: endpoints reales. Campos: nombre, url (obligatorios); metodo (GET por defecto), esperado, headers [:], body
        endpoints = [
            [nombre: 'Health API',       url: 'https://api.empresa.com/health'],
            [nombre: 'Listar clientes',  url: 'https://api.empresa.com/v1/clientes', metodo: 'GET',
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
