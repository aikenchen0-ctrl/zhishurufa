package com.osfans.trime.ime.bar

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class QuickBarStateMachineTest : StringSpec({
    "short suggestions make the candidate surface visible without Rime candidates" {
        val states = mutableListOf<QuickBarStateMachine.State>()
        val machine = QuickBarStateMachine.new { states += it }

        machine.push(
            QuickBarStateMachine.TransitionEvent.SuggestionsUpdated,
            QuickBarStateMachine.BooleanKey.CandidateEmpty to true,
            QuickBarStateMachine.BooleanKey.SuggestionEmpty to false,
        )

        machine.currentState shouldBe QuickBarStateMachine.State.Candidate
        states shouldBe listOf(QuickBarStateMachine.State.Candidate)
    }

    "clearing suggestions returns to the toolbar when Rime is also empty" {
        val machine = QuickBarStateMachine.new { }
        machine.push(
            QuickBarStateMachine.TransitionEvent.SuggestionsUpdated,
            QuickBarStateMachine.BooleanKey.CandidateEmpty to true,
            QuickBarStateMachine.BooleanKey.SuggestionEmpty to false,
        )
        machine.push(
            QuickBarStateMachine.TransitionEvent.SuggestionsUpdated,
            QuickBarStateMachine.BooleanKey.CandidateEmpty to true,
            QuickBarStateMachine.BooleanKey.SuggestionEmpty to true,
        )

        machine.currentState shouldBe QuickBarStateMachine.State.Always
    }
})

