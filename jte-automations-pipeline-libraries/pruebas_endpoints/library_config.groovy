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
        autenticaciones = List      // OAuth 2.0: [nombre, tokenUrl, credentialsId, scope, grantType]; ausente = sin OAuth
    }
}
