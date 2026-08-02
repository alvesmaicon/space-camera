package com.spacecamera

import android.app.Application
import android.util.Log
import timber.log.Timber

/**
 * Ponto de entrada do processo. Existe para configurar o logging antes de
 * qualquer outra coisa rodar.
 *
 * O Timber já estava no classpath desde o começo do projeto mas nunca havia sido
 * plantado — o código usava `android.util.Log` direto, com uma tag diferente por
 * classe, e `printStackTrace()` em alguns pontos (que só escreve no stderr e
 * costuma sumir do logcat).
 */
class SpaceCameraApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(if (BuildConfig.DEBUG) DebugTree() else ReleaseTree())
        Timber.i(
            "app start version=%s (%d) build=%s",
            BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.GIT_SHA
        )
    }

    /**
     * Tag fixa [LOG_TAG] em tudo, com a classe de origem no corpo da mensagem.
     *
     * A `DebugTree` padrão do Timber usa o nome da classe como tag, o que espalha
     * as linhas do app por dezenas de tags e impede filtrar tudo de uma vez. Com
     * tag fixa, `adb logcat -s SpaceCam` (ou `scripts/logcat.sh`) pega o app
     * inteiro e nada além dele.
     */
    private class DebugTree : Timber.DebugTree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val origem = tag ?: "?"
            super.log(priority, LOG_TAG, "[$origem] $message", t)
        }
    }

    /**
     * Em release descarta VERBOSE/DEBUG e mantém INFO+.
     *
     * Log de debug em produção custa I/O e pode vazar detalhe de uso (caminhos de
     * arquivo, URIs de mídia, localização) no logcat, legível por ferramentas de
     * diagnóstico. Se um dia entrar Crashlytics/Sentry, o gancho é aqui.
     */
    private class ReleaseTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean =
            priority >= Log.INFO

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (!isLoggable(tag, priority)) return
            Log.println(priority, LOG_TAG, message)
            if (t != null) Log.println(priority, LOG_TAG, Log.getStackTraceString(t))
        }
    }

    companion object {
        /** Tag única do app no logcat. Usada por scripts/logcat.sh. */
        const val LOG_TAG = "SpaceCam"
    }
}
