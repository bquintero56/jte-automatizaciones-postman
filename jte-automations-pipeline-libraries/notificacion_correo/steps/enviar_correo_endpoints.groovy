void call(List resultados) {
    def fecha   = resultados ? resultados[0].fecha : ''
    def total   = resultados.size()
    def errores = resultados.findAll { it.estatus != 'OK' }.size()

    def estilo = 'border:1px solid #999;padding:8px 12px;'

    def filas = resultados.collect { r ->
        def color  = (r.estatus == 'OK') ? '#2e7d32' : '#c62828'
        def codigo = r.detalle ? "${r.codigo}<br><span style=\"font-size:12px;color:#666;\">${r.detalle}</span>" : r.codigo
        """
        <tr>
            <td style="${estilo}">${r.nombre}</td>
            <td style="${estilo}text-align:center;">${r.metodo}</td>
            <td style="${estilo}font-size:12px;">${r.url}</td>
            <td style="${estilo}text-align:center;">${r.esperado}</td>
            <td style="${estilo}text-align:center;">${codigo}</td>
            <td style="${estilo}text-align:right;">${r.tiempoMs} ms</td>
            <td style="${estilo}text-align:center;font-weight:bold;color:${color};">${r.estatus}</td>
        </tr>"""
    }.join('')

    def resumen = errores
        ? "<p>Se probaron <b>${total}</b> endpoints: <b>${total - errores}</b> OK y <b style=\"color:#c62828;\">${errores}</b> con error.</p>"
        : "<p>Se probaron <b>${total}</b> endpoints: todos respondieron lo esperado.</p>"

    def html = """
    <html>
    <body style="font-family:Arial,sans-serif;font-size:14px;color:#222;">
        <p>Estimados buenos días,</p>

        ${resumen}

        <table style="border-collapse:collapse;min-width:600px;">
            <tr style="background:#eeeeee;">
                <th style="${estilo}text-align:left;">ENDPOINT</th>
                <th style="${estilo}">MÉTODO</th>
                <th style="${estilo}text-align:left;">URL</th>
                <th style="${estilo}">ESPERADO</th>
                <th style="${estilo}">CÓDIGO</th>
                <th style="${estilo}">TIEMPO</th>
                <th style="${estilo}">ESTATUS</th>
            </tr>
            ${filas}
        </table>

        <p>Saludos.</p>
    </body>
    </html>"""

    // Copia (CC): emailext los toma del mismo campo "to" con el prefijo cc:
    def copia = (config.copia ?: '').split(',').collect { it.trim() }.findAll { it }
    def para  = ([config.destinatarios] + copia.collect { "cc:${it}" }).join(',')

    def estadoAsunto = errores ? "${errores} con error" : 'todo OK'

    emailext(
        to:       para,
        subject:  "${config.asuntoPrefijo} ${config.asuntoSufijo} - ${estadoAsunto} - ${fecha}",
        mimeType: 'text/html',
        body:     html
    )

    echo "Correo enviado a: ${config.destinatarios}" + (copia ? " | CC: ${copia.join(', ')}" : '')
}
