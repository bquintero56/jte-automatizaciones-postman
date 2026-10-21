// Obtiene un token OAuth 2.0 por cada entrada de config.autenticaciones (lo mismo que "Get New Access Token" en Postman).
// Client ID y Client Secret salen de una credencial de Jenkins "Username with password" (credentialsId) y viajan en un
// header Basic Auth (curl -u). El token queda en un archivo del workspace (lo borra clean_job_workspace) y nunca se imprime.
// proxy: opciones de curl que entrega con_proxy ('' = sin proxy); usarProxy: false en la autenticación lo omite.
// Devuelve un mapa: nombre de la autenticación → [archivo, ok, codigo, detalle].
Map call(String proxy = '') {
    def tokens          = [:]
    def autenticaciones = config.autenticaciones ?: []

    for (int i = 0; i < autenticaciones.size(); i++) {
        def a = autenticaciones[i]
        if (!a.nombre || !a.tokenUrl || !a.credentialsId) {
            error "Autenticación #${i + 1} incompleta en la configuración: nombre, tokenUrl y credentialsId son obligatorios"
        }

        def grantType = a.grantType ?: 'client_credentials'
        def archivo   = "oauth_token_${i + 1}.txt"
        def scope     = a.scope ? "--data-urlencode 'scope=${a.scope}'" : ''
        def opcProxy  = (a.usarProxy == false) ? "--noproxy '*'" : proxy

        // Los secretos se usan como variables de shell (\$OAUTH_...), no se interpolan en Groovy
        def salida = ''
        withCredentials([usernamePassword(credentialsId: a.credentialsId,
                                          usernameVariable: 'OAUTH_CLIENT_ID',
                                          passwordVariable: 'OAUTH_CLIENT_SECRET')]) {
            salida = sh(
                returnStdout: true,
                script: """#!/bin/sh
codigo=\$(curl -s -o oauth_respuesta.json -w '%{http_code}' -X POST ${opcProxy} \\
    -u "\$OAUTH_CLIENT_ID:\$OAUTH_CLIENT_SECRET" \\
    --data-urlencode 'grant_type=${grantType}' ${scope} \\
    --max-time ${config.timeoutSegundos} '${a.tokenUrl}' || true)
sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\\([^"]*\\)".*/\\1/p' oauth_respuesta.json > ${archivo} 2>/dev/null
rm -f oauth_respuesta.json
if [ -s ${archivo} ]; then echo "\$codigo SI"; else echo "\$codigo NO"; fi
"""
            ).trim()
        }

        def partes  = salida.split(' ')
        def codigo  = partes[0]
        def ok      = (partes.size() > 1 && partes[1] == 'SI')
        def detalle = ''
        if (!ok) {
            detalle = (codigo == '000')
                ? "No se obtuvo token OAuth '${a.nombre}': sin respuesta de ${a.tokenUrl}"
                : "No se obtuvo token OAuth '${a.nombre}' (el servidor respondió ${codigo})"
        }

        echo ok ? "✅ Token OAuth '${a.nombre}' obtenido (${codigo})" : "❌ ${detalle}"

        tokens[a.nombre] = [archivo: archivo, ok: ok, codigo: codigo, detalle: detalle]
    }

    return tokens
}
