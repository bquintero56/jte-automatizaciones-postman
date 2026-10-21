def resultados = []

// Cron diario a la hora de la config (hora + zonaHoraria): cada ejecución prueba todos los endpoints
programar_ejecuciones()

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                // Token y endpoints salen por el proxy de la config (si hay); el correo no lo necesita
                con_proxy { proxy ->
                    def tokens = [:]

                    stage('OAuth: obtener tokens') {
                        tokens = obtener_tokens_oauth(proxy)
                    }

                    stage('HTTP: probar endpoints') {
                        resultados = probar_endpoints(tokens, proxy)
                    }
                }

                stage('Notificación: enviar correo') {
                    enviar_correo_endpoints(resultados)
                }
            }
            catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                throw e     // conserva el estado ABORTED
            }
            catch (Exception e) {
                error("${e.message ?: e.toString()}")
            }
            finally {
                clean_job_workspace()
            }
        }
    }
}
