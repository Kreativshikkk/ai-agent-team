package com.example.agentteam.net

import com.example.agentteam.net.TeamConfig
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.util.concurrent.CopyOnWriteArrayList

@Service
class TeamStore {
    private val list = CopyOnWriteArrayList<TeamConfig>()
    fun add(cfg: TeamConfig) = list.add(cfg)
    fun all(): List<TeamConfig> = list.toList()
    companion object { fun get() = service<TeamStore>() }
}
