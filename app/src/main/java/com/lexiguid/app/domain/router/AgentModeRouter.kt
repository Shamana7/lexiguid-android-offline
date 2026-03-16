package com.lexiguid.app.domain.router

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight on-device agent mode router.
 * Replaces the ADK orchestrator's multi-agent routing with simple pattern matching.
 *
 * Mirrors backend routing rules:
 *   RULE 0: Follow-ups → RAG
 *   RULE 1: Greetings/identity → GREETING
 *   RULE 2: Academic questions with context → RAG
 *   RULE 3: General knowledge → KNOWLEDGE
 *   RULE 4: Default → RAG
 */
@Singleton
class AgentModeRouter @Inject constructor() {

    enum class Mode { GREETING, RAG, KNOWLEDGE }

    /**
     * Detect the appropriate agent mode from the user message and context.
     */
    fun detectMode(
        userMessage: String,
        hasActiveSubject: Boolean,
        hasConversationHistory: Boolean
    ): Mode {
        val msg = userMessage.lowercase().trim()

        // RULE 0: Follow-ups in an active conversation → RAG
        if (hasConversationHistory && hasActiveSubject) {
            // Unless it's a pure greeting/bye
            if (!isPureGreeting(msg)) return Mode.RAG
        }

        // RULE 1: Greetings / identity / thanks
        if (isGreeting(msg)) return Mode.GREETING

        // RULE 2: Academic question with active subject context
        if (hasActiveSubject) return Mode.RAG

        // RULE 3: General knowledge (no subject context)
        if (isGeneralQuestion(msg)) return Mode.KNOWLEDGE

        // RULE 4: Default → RAG if context exists, KNOWLEDGE otherwise
        return if (hasActiveSubject) Mode.RAG else Mode.KNOWLEDGE
    }

    private fun isGreeting(msg: String): Boolean =
        GREETING_PATTERNS.any { msg.matches(it) }

    private fun isPureGreeting(msg: String): Boolean =
        PURE_GREETING_PATTERNS.any { msg.matches(it) }

    private fun isGeneralQuestion(msg: String): Boolean =
        GENERAL_KNOWLEDGE_PATTERNS.any { it.containsMatchIn(msg) }

    companion object {
        private val GREETING_PATTERNS = listOf(
            Regex("^(hi|hello|hey|hola|namaste|howdy|greetings)[!.,?\\s]*$"),
            Regex("^(good\\s+(morning|afternoon|evening|night))[!.,?\\s]*$"),
            Regex("^(who\\s+are\\s+you|what\\s+is\\s+your\\s+name|what\\s+can\\s+you\\s+do).*"),
            Regex("^(thank|thanks|thank\\s+you|bye|goodbye|see\\s+you)[!.,?\\s]*$"),
            Regex("^(how\\s+are\\s+you|what'?s\\s+up)[!.,?\\s]*$")
        )

        private val PURE_GREETING_PATTERNS = listOf(
            Regex("^(hi|hello|hey|bye|goodbye|thanks|thank you)[!.,?\\s]*$")
        )

        private val GENERAL_KNOWLEDGE_PATTERNS = listOf(
            Regex("(who\\s+(is|was|were)\\s+)"),
            Regex("(what\\s+(is|are|was|were)\\s+the\\s+(capital|population|meaning))"),
            Regex("(tell\\s+me\\s+about)"),
            Regex("(how\\s+does\\s+.+\\s+work)"),
            Regex("(explain\\s+(the\\s+)?concept)")
        )
    }
}
