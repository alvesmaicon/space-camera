package com.spacecamera.presentation.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Requirements: FR-1
 *
 * Fonte única de verdade do layout adaptativo: a janela é larga quando a largura em
 * dp supera a altura em dp.
 *
 * O empate favorece **retrato** de propósito (AC-1.3). Retrato é o caminho default
 * do código e o único com cobertura de teste, então qualquer valor inesperado —
 * janela quadrada, dimensões ainda não medidas — cai no layout que já funciona
 * (NFR-1).
 *
 * Função pura para rodar na JVM (NFR-4). A leitura da configuração fica em
 * [rememberIsWideWindow].
 */
internal fun isWideWindow(widthDp: Int, heightDp: Int): Boolean = widthDp > heightDp

/**
 * Requirements: FR-1
 *
 * Classifica a janela atual, reavaliando a cada mudança de configuração —
 * `LocalConfiguration` é reemitido pelo Compose quando a janela muda de tamanho ou
 * orientação.
 *
 * Usa `LocalConfiguration` em vez de `WindowSizeClass` (ADR-002): a spec precisa de
 * um booleano, não de faixas de largura, e essa já é a fonte usada para dimensionar
 * a pré-visualização. Isolado neste ponto único para permitir a troca se um dia
 * houver um terceiro layout.
 */
@Composable
internal fun rememberIsWideWindow(): Boolean {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    return remember(widthDp, heightDp) { isWideWindow(widthDp, heightDp) }
}

/**
 * Decisions: ADR-003
 *
 * Escopo agnóstico de eixo. `Modifier.weight` existe em `RowScope` e em
 * `ColumnScope`, mas como funções de extensão **distintas** — o mesmo composable não
 * serve aos dois eixos sem tratamento. [axisWeight] delega ao escopo concreto e
 * mantém os call sites idênticos aos de hoje.
 */
internal interface AxisScope {
    /** Distribui o espaço do eixo principal, seja ele a largura ou a altura. */
    fun Modifier.axisWeight(weight: Float = 1f): Modifier
}

private class RowAxisScope(private val scope: RowScope) : AxisScope {
    override fun Modifier.axisWeight(weight: Float): Modifier =
        with(scope) { this@axisWeight.weight(weight) }
}

private class ColumnAxisScope(private val scope: ColumnScope) : AxisScope {
    override fun Modifier.axisWeight(weight: Float): Modifier =
        with(scope) { this@axisWeight.weight(weight) }
}

/**
 * Decisions: ADR-003
 *
 * `Row` ou `Column` conforme [vertical], fornecendo [AxisScope] ao conteúdo.
 *
 * Com `vertical = false` e [spacing] zerado, produz exatamente o `Row` que
 * substitui: o alinhamento no eixo cruzado é centralizado (o que todos os call
 * sites já pediam) e a distribuição no eixo principal fica no default do Compose.
 */
@Composable
internal fun AxisContainer(
    vertical: Boolean,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    content: @Composable AxisScope.() -> Unit
) {
    if (vertical) {
        Column(
            modifier = modifier,
            verticalArrangement =
                if (spacing > 0.dp) Arrangement.spacedBy(spacing) else Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ColumnAxisScope(this).content()
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement =
                if (spacing > 0.dp) Arrangement.spacedBy(spacing) else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RowAxisScope(this).content()
        }
    }
}
