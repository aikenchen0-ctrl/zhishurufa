// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.sdk.SuggestionItem
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add

class CandidateUi(
    override val ctx: Context,
    private val scope: ThemeScope,
    private val compatView: View,
    onSuggestionClick: (SuggestionItem) -> Unit = {},
) : Ui {
    private val theme: Theme get() = scope.theme

    val unrollButton =
        ToolButton(ctx, R.drawable.ic_baseline_expand_more_24, scope).apply {
            visibility = View.INVISIBLE
        }

    private val suggestionUi = SuggestionUi(ctx, scope, onSuggestionClick)

    override val root =
        ctx.constraintLayout {
            add(
                unrollButton,
                lParams(dp(40)) {
                    centerVertically()
                    endOfParent()
                },
            )
            add(
                compatView,
                lParams {
                    centerVertically()
                    startOfParent(dp(theme.generalStyle.candidatePadding / 2))
                    before(unrollButton)
                },
            )
            add(
                suggestionUi.root,
                lParams {
                    centerVertically()
                    startOfParent(dp(theme.generalStyle.candidatePadding / 2))
                    before(unrollButton)
                },
            )
        }

    /** 只在没有 Rime 候选时展示短建议，避免改变候选索引和点击语义。 */
    fun setSuggestions(items: List<SuggestionItem>) {
        compatView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        suggestionUi.setItems(items)
    }

    /** Restyles the candidate bar after a scheme switch. */
    fun refreshColors() {
        unrollButton.refreshColors()
        suggestionUi.refreshColors()
    }
}
