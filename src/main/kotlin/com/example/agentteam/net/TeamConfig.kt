package com.example.agentteam.net

import kotlinx.serialization.Serializable

// Assuming TeamConfig is defined elsewhere, you'll need to update it like this:
@Serializable
data class TeamConfig(
    val task: String,
    val teamLeads: Int,
    val techLeads: Int,
    val engineers: Int,
    val qaEngineers: Int,
    val globalPrompt: String,
    val rolePrompts: Map<String, String>,
    // Add models map to store model selections
    val roleModels: Map<String, String> = mapOf()
)