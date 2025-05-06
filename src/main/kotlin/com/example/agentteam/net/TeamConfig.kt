package com.example.agentteam.net

import kotlinx.serialization.Serializable

@Serializable
data class TeamConfig(
    val task: String,
    val teamLeads: Int,
    val techLeads: Int,
    val engineers: Int,

    /** общий дополнительный prompt, который вводится ПОСЛЕ Submit */
    val globalPrompt: String = "",

    /**
     * индивидуальные prompt’ы на роль:
     *   "teamLead" → "…"
     *   "techLead" → "…"
     *   "engineer" → "…"
     */
    val rolePrompts: Map<String, String> = emptyMap()
)
