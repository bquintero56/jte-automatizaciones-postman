def resultados = []

// Cron diario a la hora de la config (hora + zonaHoraria): cada ejecución prueba todos los endpoints
programar_ejecuciones()

timeout(time: execution.time, unit: execution.units) {

    node(jenkins_agent_label) {
        withFolderProperties {
            try {
                def tokens = [:]

                stage('OAuth: obtener tokens') {
                    tokens = obtener_tokens_oauth()
                }

                stage('HTTP: probar endpoints') {
                    resultados = probar_endpoints(tokens)
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
