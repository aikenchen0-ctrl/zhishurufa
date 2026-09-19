/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar

import com.osfans.trime.ime.bar.QuickBarStateMachine.BooleanKey.CandidateEmpty
import com.osfans.trime.ime.bar.QuickBarStateMachine.BooleanKey.SuggestionEmpty
import com.osfans.trime.ime.bar.QuickBarStateMachine.State.Always
import com.osfans.trime.ime.bar.QuickBarStateMachine.State.Candidate
import com.osfans.trime.ime.bar.QuickBarStateMachine.State.Tab
import com.osfans.trime.util.BuildTransitionEvent
import com.osfans.trime.util.EventStateMachine
import com.osfans.trime.util.TransitionBuildBlock

object QuickBarStateMachine {
    enum class State {
        Always,
        Candidate,
        Tab,
    }

    enum class BooleanKey : EventStateMachine.BooleanStateKey {
        CandidateEmpty,
        SuggestionEmpty,
    }

    enum class TransitionEvent(
        val builder: TransitionBuildBlock<State, BooleanKey>,
    ) : EventStateMachine.TransitionEvent<State, BooleanKey> by BuildTransitionEvent(builder) {
        CandidatesUpdated({
            from(Always) transitTo Candidate onF { states ->
                states(CandidateEmpty) == false || states(SuggestionEmpty) == false
            }
            from(Candidate) transitTo Always onF { states ->
                states(CandidateEmpty) == true && states(SuggestionEmpty) == true
            }
        }),
        SuggestionsUpdated({
            from(Always) transitTo Candidate onF { states ->
                states(CandidateEmpty) == false || states(SuggestionEmpty) == false
            }
            from(Candidate) transitTo Always onF { states ->
                states(CandidateEmpty) == true && states(SuggestionEmpty) == true
            }
        }),
        BarBoardWindowAttached({
            from(Always) transitTo Tab
            from(Candidate) transitTo Tab
        }),
        WindowDetached({
            // candidate state has higher priority so here it goes first
            from(Tab) transitTo Candidate onF { states ->
                states(CandidateEmpty) == false || states(SuggestionEmpty) == false
            }
            from(Tab) transitTo Always
        }),
    }

    fun new(block: (State) -> Unit) = EventStateMachine<State, TransitionEvent, BooleanKey>(
        initialState = Always,
        externalBooleanStates =
        mutableMapOf(
            CandidateEmpty to true,
            SuggestionEmpty to true,
        ),
    ).apply {
        onNewStateListener = block
    }
}
