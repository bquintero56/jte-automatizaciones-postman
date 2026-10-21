fields {
    required {
        zonaHoraria     = String
        hora            = String
        codigoEsperado  = Integer
        timeoutSegundos = Integer
        estadoSiFallan  = String
        endpoints       = List
    }
    optional {
        autenticaciones    = List      // OAuth 2.0: [nombre, tokenUrl, credentialsId, scope, grantType, usarProxy]; ausente = sin OAuth
        proxy              = String    // http://host:puerto; vacío o ausente = sin proxy
        proxyCredentialsId = String    // credencial "Username with password" si el proxy pide usuario; vacío = sin usuario
    }
}
