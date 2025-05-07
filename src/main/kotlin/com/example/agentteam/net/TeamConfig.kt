package com.example.agentteam.net

import kotlinx.serialization.Serializable

@Serializable
data class TeamConfig(
    val task: String,
    val teamLeads: Int,
    val softwareEngineers: Int,
    val qaEngineers: Int,

    /** общий дополнительный prompt, который вводится ПОСЛЕ Submit */
    val globalPrompt: String = "",

    /**
     * индивидуальные prompt’ы на роль:
     *   "teamLead" → "…"
     *   "techLead" → "…"
     *   "engineer" → "…"
     *   "qaEngineer" → "…"
     */
    val rolePrompts: Map<String, String> = emptyMap()
)
