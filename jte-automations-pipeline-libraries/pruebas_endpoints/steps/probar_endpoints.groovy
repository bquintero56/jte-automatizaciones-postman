// Prueba cada endpoint de config.endpoints con curl y compara el código HTTP con el esperado.
// tokens: lo que devuelve obtener_tokens_oauth() (nombre de la autenticación → [archivo, ok, codigo, detalle]).
// proxy: opciones de curl que entrega con_proxy ('' = sin proxy); usarProxy: false en el endpoint lo omite.
// Devuelve una lista; por endpoint: [nombre, metodo, url, esperado, codigo, tiempoMs, estatus, detalle, fecha].
List call(Map tokens = [:], String proxy = '') {
    def resultados = []
    def errores    = []

    // Fecha y hora de la ejecución en la zona horaria configurada (para el correo)
    def fecha = sh(
        returnStdout: true,
        script: """#!/bin/sh
TZ='${config.zonaHoraria}' date +'%Y-%m-%d %H:%M'
"""
    ).trim()

    echo "Fecha: ${fecha} | Endpoints a probar: ${config.endpoints.size()} | Timeout: ${config.timeoutSegundos} s"

    for (int i = 0; i < config.endpoints.size(); i++) {
        def ep       = config.endpoints[i]
        def metodo   = (ep.metodo ?: 'GET').toString().toUpperCase()
        def esperado = (ep.esperado ?: config.codigoEsperado) as Integer
        def opcProxy = (ep.usarProxy == false) ? "--noproxy '*'" : proxy

        // Cabeceras: una opción -H por cada una
        def headers = ''
        if (ep.headers) {
            for (def k in ep.headers.keySet()) {
                headers += " -H '${k}: ${ep.headers[k]}'"
            }
        }

        // OAuth 2.0: el sh lee el token del archivo, así no pasa por Groovy ni aparece en el log
        def detalleAuth = ''
        if (ep.auth) {
            def t = tokens[ep.auth]
            if (!t) {
                detalleAuth = "Autenticación '${ep.auth}' no definida en autenticaciones"
            } else if (!t.ok) {
                detalleAuth = t.detalle
            } else {
                headers += " -H \"Authorization: Bearer \$(cat ${t.archivo})\""
            }
        }

        // El cuerpo va a un archivo para no tener problemas con comillas dentro del JSON
        def data = ''
        if (ep.body) {
            writeFile file: 'endpoint_body.txt', text: ep.body.toString()
            data = '--data-binary @endpoint_body.txt'
        }

        // -w imprime "codigo tiempo"; si no hay respuesta (DNS, conexión, timeout) curl devuelve 000.
        // Sin token no se llama al endpoint: queda en ERROR con el motivo.
        def salida = '000 0'
        if (!detalleAuth) {
            salida = sh(
                returnStdout: true,
                script: """#!/bin/sh
curl -s -o /dev/null -w '%{http_code} %{time_total}' -X ${metodo} ${opcProxy} ${headers} ${data} \\
    --max-time ${config.timeoutSegundos} '${ep.url}' || true
"""
            ).trim()
        }

        def partes   = salida.split(' ')
        def codigo   = partes[0]
        def tiempoMs = (partes.size() > 1) ? Math.round(partes[1].toDouble() * 1000) : 0
        def ok       = (codigo == esperado.toString())
        def detalle  = detalleAuth ?: ((codigo == '000') ? 'Sin respuesta (DNS, conexión o timeout)' : '')

        if (ok) {
            echo "✅ [${metodo}] ${ep.nombre} → ${codigo} (${tiempoMs} ms)"
        } else {
            echo "❌ [${metodo}] ${ep.nombre} → ${codigo}, se esperaba ${esperado} (${tiempoMs} ms) ${detalle}".trim()
            errores << "${ep.nombre} (${codigo})"
        }

        resultados << [
            nombre:   ep.nombre,
            metodo:   metodo,
            url:      ep.url,
            esperado: esperado,
            codigo:   codigo,
            tiempoMs: tiempoMs,
            estatus:  ok ? 'OK' : 'ERROR',
            detalle:  detalle,
            fecha:    fecha
        ]
    }

    echo "=========== RESUMEN ==========="
    echo "OK:     ${resultados.size() - errores.size()} de ${resultados.size()}"
    echo "Error:  ${errores.size()} de ${resultados.size()}"

    if (errores) {
        echo "Con error:\n - ${errores.join('\n - ')}"

        // No se usa error(): el pipeline debe seguir hasta enviar el correo
        switch (config.estadoSiFallan) {
            case 'FAILURE':
                echo "Fallaron ${errores.size()} endpoint(s): el build se marca como FAILURE"
                currentBuild.result = 'FAILURE'
                break
            case 'UNSTABLE':
                unstable("Fallaron ${errores.size()} endpoint(s)")
                break
            default:
                break
        }
    } else {
        echo "Todos los endpoints respondieron lo esperado"
    }

    return resultados
}
