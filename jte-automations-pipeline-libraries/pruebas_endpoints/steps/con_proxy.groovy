// Ejecuta el bloque recibiendo las opciones de proxy para curl, según config.proxy y config.proxyCredentialsId.
// Sin proxy en la config el bloque recibe '' (curl usa la red o las variables http(s)_proxy del nodo).
// Si el proxy pide usuario, sale de una credencial Jenkins "Username with password" y va como variables de shell
// (\$PROXY_USER / \$PROXY_PASS): nunca se interpola en Groovy ni aparece en el log.
def call(Closure body) {
    if (!config.proxy) {
        echo "Proxy: no configurado"
        return body('')
    }

    def opciones = "--proxy '${config.proxy}'"

    if (!config.proxyCredentialsId) {
        echo "Proxy: ${config.proxy}"
        return body(opciones)
    }

    echo "Proxy: ${config.proxy} (con usuario de la credencial '${config.proxyCredentialsId}')"
    def resultado
    withCredentials([usernamePassword(credentialsId: config.proxyCredentialsId,
                                      usernameVariable: 'PROXY_USER',
                                      passwordVariable: 'PROXY_PASS')]) {
        resultado = body(opciones + ' --proxy-user "$PROXY_USER:$PROXY_PASS"')
    }
    return resultado
}
