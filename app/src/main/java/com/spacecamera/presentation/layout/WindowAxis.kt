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
    /**
     * Qual eixo está em uso. Exposto porque distribuir com peso só faz sentido no
     * horizontal: numa coluna da altura da tela, o peso espalha os controles por
     * centenas de pixels e o grupo deixa de parecer um grupo.
     */
    val vertical: Boolean

    /** Distribui o espaço do eixo principal, seja ele a largura ou a altura. */
    fun Modifier.axisWeight(weight: Float = 1f): Modifier
}

private class RowAxisScope(private val scope: RowScope) : AxisScope {
    override val vertical = false
    override fun Modifier.axisWeight(weight: Float): Modifier =
        with(scope) { this@axisWeight.weight(weight) }
}

private class ColumnAxisScope(private val scope: ColumnScope) : AxisScope {
    override val vertical = true
    override fun Modifier.axisWeight(weight: Float): Modifier =
        with(scope) { this@axisWeight.weight(weight) }
}

/**
 * Decisions: ADR-003
 *
 * `Row` ou `Column` conforme [vertical], fornecendo [AxisScope] ao conteúdo.
 *
 * [arrangement] é tipado como `Arrangement.HorizontalOrVertical` justamente porque é
 * o subconjunto que serve aos dois eixos — `SpaceAround`, `SpaceEvenly`,
 * `SpaceBetween`, `Center` e `spacedBy`. `Start` e `Top`, que existem em só um eixo,
 * ficam de fora por construção: são o default de cada eixo quando não se passa nada.
 *
 * Com `vertical = false` e `arrangement` nulo, produz exatamente o `Row` que
 * substitui — alinhamento centralizado no eixo cruzado, que é o que todos os call
 * sites já pediam.
 */
@Composable
internal fun AxisContainer(
    vertical: Boolean,
    modifier: Modifier = Modifier,
    arrangement: Arrangement.HorizontalOrVertical? = null,
    content: @Composable AxisScope.() -> Unit
) {
    if (vertical) {
        Column(
            modifier = modifier,
            verticalArrangement = arrangement ?: Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ColumnAxisScope(this).content()
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = arrangement ?: Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RowAxisScope(this).content()
        }
    }
}
