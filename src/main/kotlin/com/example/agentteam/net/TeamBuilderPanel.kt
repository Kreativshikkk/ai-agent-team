package com.example.agentteam.net

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import java.util.regex.Pattern

private data class Msg(val isUser: Boolean, val text: String)

class TeamBuilderPanel(private val project: Project) {
    private var lastMessageHeader = "Internies"

    private fun stripAnsiCodes(text: String): String {
        val ansiPattern = Pattern.compile("\u001B\\[[;\\d]*m")
        return ansiPattern.matcher(text).replaceAll("")
    }

    private val tlSpin = JSpinner(SpinnerNumberModel(1, 1, 1, 1)).apply { isEnabled = false }
    private val engSpin = JSpinner(SpinnerNumberModel(1, 0, 99, 1))
    private val qaSpin = JSpinner(SpinnerNumberModel(1, 0, 99, 1))
    private val taskArea = JTextArea(3, 60)
    private val rolePrompts = mutableMapOf<String, String>()
    private var firstMessageSent = false

    private val roleModelSelectors = mutableMapOf<String, JComboBox<String>>()

    private lateinit var plusButton: JButton
    private lateinit var createBtn: JButton
    private lateinit var chatButton: JButton

    private lateinit var chatContainer: JPanel
    private lateinit var inputField: JTextArea

    private val rolesPanel = createRolesPanel()
    private val chatPanel = createChatPanel()
    private val cardPanel = JPanel(CardLayout()).apply {
        add(rolesPanel, "ROLES")
        add(chatPanel, "CHAT")
    }

    val component: JPanel = cardPanel

    private fun createModelSelector(): JComboBox<String> {
        val models = arrayOf("Claude 3.5 Sonnet", "Claude 3 Opus", "GPT-4o", "GPT-3.5 Turbo")
        return JComboBox(models).apply {
            // Увеличиваем ширину до 180 пикселей, чтобы текст полностью помещался
            preferredSize = Dimension(180, 25)
            maximumSize = preferredSize
        }
    }

    private fun createRolesPanel(): JPanel = JPanel(BorderLayout()).apply {
        chatButton = JButton("Chat").apply {
            toolTipText = "Return to chat"
            addActionListener { showChatScreen() }
            isVisible = false
        }
        add(
            JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                border = EmptyBorder(4, 8, 4, 8)
                add(chatButton)
            },
            BorderLayout.NORTH
        )

        val icon = IconLoader.getIcon("/icons/banner.png", TeamBuilderPanel::class.java)
        val bannerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(JLabel(icon))
            preferredSize = Dimension(icon.iconWidth, icon.iconHeight)
            maximumSize = preferredSize
        }

        val welcomeLabel = JLabel("Welcome! Let's Kickstart Your Agent Team!").apply {
            horizontalAlignment = SwingConstants.CENTER
            alignmentX = Component.CENTER_ALIGNMENT
            font = font.deriveFont(Font.BOLD, 16f)
            foreground = JBColor.foreground()
            border = EmptyBorder(10, 0, 10, 0)
        }

        val centerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(bannerPanel)
            add(welcomeLabel)
        }
        add(centerPanel, BorderLayout.CENTER)

        val rolesBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
            add(roleRow("Team-leads", tlSpin, "teamLead")); add(Box.createVerticalStrut(8))
            add(roleRow("Software Engineers", engSpin, "engineer")); add(Box.createVerticalStrut(8))
            add(roleRow("QA Engineers", qaSpin, "qaEngineer")); add(Box.createVerticalStrut(8))
        }
        add(rolesBox, BorderLayout.CENTER)

        plusButton = JButton("+").apply {
            toolTipText = "Add new role"
            addActionListener {
                val roleField     = JTextField()
                val goalField     = JTextField()
                val backstoryArea = JTextArea(3, 20).apply {
                    lineWrap      = true
                    wrapStyleWord = true
                }
                val modelSelector = createModelSelector()

                val form = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    border = EmptyBorder(8, 8, 8, 8)
                    add(JLabel("role"))
                    add(roleField); add(Box.createVerticalStrut(6))
                    add(JLabel("goal"))
                    add(goalField); add(Box.createVerticalStrut(6))
                    add(JLabel("backstory"))
                    add(JScrollPane(backstoryArea).apply {
                        verticalScrollBarPolicy   = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
                        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                        preferredSize = Dimension(-1, 60)
                    })
                    add(Box.createVerticalStrut(6))
                    add(JLabel("model"))
                    add(modelSelector)
                }

                val builder = com.intellij.openapi.ui.DialogBuilder(project).apply {
                    setTitle("Create New Role")
                    setCenterPanel(form)
                    removeAllActions()
                    addOkAction()
                    addCancelAction()
                }

                if (builder.show() != com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE) {
                    return@addActionListener
                }

                val role  = roleField.text.trim().takeIf(String::isNotEmpty) ?: return@addActionListener
                val goal      = goalField.text.trim()
                val backstory = backstoryArea.text.trim()
                val selectedModel = modelSelector.selectedItem as? String ?: "Unknown model"

                val spinner = JSpinner(SpinnerNumberModel(1, 0, 99, 1))
                val key     = role.replace("\\s+".toRegex(), "").replaceFirstChar { it.lowercase() }
                rolePrompts[key] = """
      role: $role
      goal: $goal
      backstory: $backstory
    """.trimIndent()

                try {
                    val newModelSelector = createModelSelector().apply {
                        selectedItem = selectedModel
                    }
                    roleModelSelectors[key] = newModelSelector
                } catch (e: Exception) {
                }

                try {
                    rolesBox.add(roleRow(role, spinner, key))
                    rolesBox.add(Box.createVerticalStrut(8))
                    rolesBox.revalidate()
                    rolesBox.repaint()
                } catch (e: Exception) {
                }
            }
        }

        createBtn = JButton("Create Team").apply {
            font = font.deriveFont(Font.BOLD, 16f)
            preferredSize = Dimension(-1, 100)
            addActionListener {
                onSubmitRoles()
            }
        }

        add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    border = EmptyBorder(0, 8, 4, 8)
                    add(plusButton)
                })
                add(JPanel(BorderLayout()).apply {
                    border = EmptyBorder(4, 8, 8, 8)
                    add(createBtn, BorderLayout.CENTER)
                })
            }, BorderLayout.SOUTH
        )
    }

    private fun roleRow(label: String, spinner: JSpinner, key: String): JPanel = JPanel(BorderLayout()).apply {
        alignmentX = Component.LEFT_ALIGNMENT

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(hyperlink(label) {
                val p = Messages.showInputDialog(
                    project, "Prompt for $label:", "Custom Prompt", null, rolePrompts[key] ?: "", null
                ) ?: return@hyperlink
                rolePrompts[key] = p.trim()
            })
        }

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
            isOpaque = false

            if (!roleModelSelectors.containsKey(key)) {
                val modelSelector = createModelSelector()
                roleModelSelectors[key] = modelSelector
            }

            // Сначала добавляем спиннер, а затем селектор модели (изменён порядок)
            add(spinner)
            val modelSelector = roleModelSelectors[key]
            add(modelSelector)
        }

        add(leftPanel, BorderLayout.WEST)
        add(rightPanel, BorderLayout.EAST)
        maximumSize = preferredSize
        return@apply
    }

    private fun onSubmitRoles() {
        val selectedModels = mutableMapOf<String, String>()

        roleModelSelectors.forEach { (key, selector) ->
            val selectedModel = selector.selectedItem as? String ?: "Unknown"
            selectedModels[key] = selectedModel
        }

        val config = TeamConfig(
            task = taskArea.text.trim(),
            teamLeads = tlSpin.value as Int,
            techLeads = 0,
            engineers = engSpin.value as Int,
            qaEngineers = qaSpin.value as Int,
            globalPrompt = "",
            rolePrompts = rolePrompts.toMap(),
            roleModels = selectedModels
        )
        TeamStore.get().add(config)

        try {
            val crewGenerator = PythonCrewGenerator(project)
            crewGenerator.generateJsonFile(config)
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to generate JSON crew file: ${e.message}", "Internies")
        }

        chatButton.isVisible = true
        rolesPanel.revalidate()
        rolesPanel.repaint()
        showChatScreen()
    }

    private fun createChatPanel(): JPanel = JPanel(BorderLayout()).apply {
        val viewButton = JButton("View Team").apply {
            toolTipText = "View team composition"
            addActionListener { showReadOnlyRoles() }
        }

        val topPanel = JPanel(BorderLayout())
        topPanel.add(
            JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                border = EmptyBorder(4, 8, 4, 8)
                add(viewButton)
            },
            BorderLayout.NORTH
        )

        topPanel.add(JSeparator(SwingConstants.HORIZONTAL), BorderLayout.SOUTH)

        add(topPanel, BorderLayout.NORTH)
        chatContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
        }
        val infoPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(0, 8, 12, 8)
            add(JLabel("This is your new chat, here you will communicate with agents").apply {
                alignmentX = Component.CENTER_ALIGNMENT
                font = font.deriveFont(Font.ITALIC, 12f)
                foreground = JBColor.GRAY
            })
        }
        chatContainer.add(Box.createVerticalGlue())

        chatContainer.add(infoPanel)

        chatContainer.add(Box.createVerticalGlue())
        val chatScroll = JScrollPane(
            chatContainer, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        ).apply { preferredSize = Dimension(-1, 100) }
        add(chatScroll, BorderLayout.CENTER)
        inputField = JTextArea(3, 60).apply {
            lineWrap = true
            wrapStyleWord = true
        }
        add(JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 8, 8)
            val inputScroll = JScrollPane(
                inputField, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ).apply { preferredSize = Dimension(-1, 100) }
            add(inputScroll, BorderLayout.CENTER)
            add(
                JButton("Send").apply { addActionListener { onSend() } }, BorderLayout.EAST
            )
        }, BorderLayout.SOUTH)
    }

    private fun showChatScreen() {
        inputField.text = ""
        (cardPanel.layout as CardLayout).show(cardPanel, "CHAT")
        cardPanel.revalidate(); cardPanel.repaint()
    }

    private fun showReadOnlyRoles() {
        val configs = TeamStore.get().all()
        if (configs.isNotEmpty()) {
            val latestConfig = configs.last()

            tlSpin.value = latestConfig.teamLeads
            engSpin.value = latestConfig.engineers
            qaSpin.value = latestConfig.qaEngineers
            taskArea.text = latestConfig.task

            rolePrompts.clear()
            rolePrompts.putAll(latestConfig.rolePrompts)

            latestConfig.roleModels.forEach { (key, model) ->
                val selector = roleModelSelectors[key]
                if (selector != null) {
                    try {
                        selector.selectedItem = model
                    } catch (e: Exception) {
                    }
                }
            }
        }

        (cardPanel.layout as CardLayout).show(cardPanel, "ROLES")
        tlSpin.isEnabled = false
        engSpin.isEnabled = false
        qaSpin.isEnabled = false
        plusButton.isEnabled = false
        createBtn.isEnabled = false
        chatButton.isVisible = true

        roleModelSelectors.values.forEach {
            it.isEnabled = false
        }

        cardPanel.revalidate()
        cardPanel.repaint()
    }

    private fun getScrollPaneFor(component: Component): JScrollPane {
        return SwingUtilities.getAncestorOfClass(JScrollPane::class.java, component) as JScrollPane
    }

    private fun scrollChatToBottom() {
        SwingUtilities.invokeLater {
            val scrollPane = getScrollPaneFor(chatContainer)
            val vsb = scrollPane.verticalScrollBar
            vsb.value = vsb.maximum
        }
    }

    private fun scrollChatToBottomInUIThread() {
        val scrollPane = getScrollPaneFor(chatContainer)
        val vsb = scrollPane.verticalScrollBar
        vsb.value = vsb.maximum
    }

    private fun onSend() {
        val txt = inputField.text.trim().takeIf(String::isNotEmpty) ?: return
        inputField.text = ""

        if (!firstMessageSent) {
            chatContainer.removeAll()
            firstMessageSent = true
        }

        addBubble(isUser = true, text = txt)

        addBubble(isUser = false, text = "Got your request and sending it to the team...")
        chatContainer.revalidate()
        chatContainer.repaint()

        scrollChatToBottom()

        try {
            val pythonScriptPath = "${ConfigUtil.getPythonScriptsPath()}/team.py"
            val process = ProcessBuilder("python3.11", pythonScriptPath, txt).redirectErrorStream(true).start()

            Thread {
                try {
                    val reader = process.inputStream.bufferedReader()
                    var collectingMessage = false
                    var currentMessage = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        println(line)
                        if (line!!.contains("# Agent:")) {
                            collectingMessage = true

                            if (currentMessage.isNotEmpty()) {
                                val messageText = stripAnsiCodes(currentMessage.toString().trim())
                                SwingUtilities.invokeLater {
                                    addBubble(isUser = false, text = messageText)
                                    chatContainer.revalidate()
                                    chatContainer.repaint()
                                    scrollChatToBottom()
                                }
                                currentMessage = StringBuilder()
                            }

                            val cleanLine = stripAnsiCodes(line!!)
                            val agentPart = cleanLine.substringAfter("# Agent:")

                            if (agentPart.contains("🤖")) {
                                val beforeEmoji = agentPart.substringBefore("🤖")
                                if (beforeEmoji.isNotEmpty()) {
                                    if (!beforeEmoji.contains("🚀 Crew:") &&
                                        !beforeEmoji.contains("├──") &&
                                        !beforeEmoji.contains("│") &&
                                        !beforeEmoji.contains("└──")) {
                                        currentMessage.append(beforeEmoji)
                                    }
                                }

                                val messageText = stripAnsiCodes(currentMessage.toString().trim())
                                if (messageText.isNotEmpty()) {
                                    SwingUtilities.invokeLater {
                                        addBubble(isUser = false, text = messageText)
                                        chatContainer.revalidate()
                                        chatContainer.repaint()
                                        val vsb = (chatContainer.parent as JScrollPane).verticalScrollBar
                                        vsb.value = vsb.maximum
                                    }
                                }

                                collectingMessage = false
                                currentMessage = StringBuilder()
                            } else {
                                if (agentPart.trim().isNotEmpty()) {
                                    if (!agentPart.contains("🚀 Crew:") &&
                                        !agentPart.contains("├──") &&
                                        !agentPart.contains("│") &&
                                        !agentPart.contains("└──")) {
                                        currentMessage.append(agentPart).append("\n")
                                    }
                                }
                            }
                        } else if (collectingMessage) {
                            if (line!!.contains("🤖")) {
                                val beforeEmoji = line!!.substringBefore("🤖")
                                if (beforeEmoji.isNotEmpty()) {
                                    if (!beforeEmoji.contains("🚀 Crew:") &&
                                        !beforeEmoji.contains("├──") &&
                                        !beforeEmoji.contains("│") &&
                                        !beforeEmoji.contains("└──")) {
                                        currentMessage.append(beforeEmoji)
                                    }
                                }

                                val messageText = stripAnsiCodes(currentMessage.toString().trim())
                                if (messageText.isNotEmpty()) {
                                    SwingUtilities.invokeLater {
                                        addBubble(isUser = false, text = messageText)
                                        chatContainer.revalidate()
                                        chatContainer.repaint()
                                        val vsb = (chatContainer.parent as JScrollPane).verticalScrollBar
                                        vsb.value = vsb.maximum
                                    }
                                }

                                collectingMessage = false
                                currentMessage = StringBuilder()
                            } else {
                                if (!line!!.startsWith("🚀 Crew:") &&
                                    !line!!.startsWith("├──") &&
                                    !line!!.startsWith("│") &&
                                    !line!!.startsWith("└──")) {
                                    currentMessage.append(line).append("\n")
                                }
                            }
                        }
                    }

                    if (collectingMessage && currentMessage.isNotEmpty()) {
                        val messageText = stripAnsiCodes(currentMessage.toString().trim())
                        SwingUtilities.invokeLater {
                            addBubble(isUser = false, text = messageText)
                            chatContainer.revalidate()
                            chatContainer.repaint()
                            val vsb = (chatContainer.parent as JScrollPane).verticalScrollBar
                            vsb.value = vsb.maximum
                        }
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        addBubble(isUser = false, text = "Error reading script output: ${e.message}")
                        chatContainer.revalidate()
                        chatContainer.repaint()
                    }
                }
            }.start()

        } catch (e: Exception) {
            val jsonFilePath = "${ConfigUtil.getPythonScriptsPath()}/crew.json"
            addBubble(
                isUser = false,
                text = "Error running script: ${e.message}\nNote: A JSON file has been generated at $jsonFilePath"
            )
            chatContainer.revalidate()
            chatContainer.repaint()
        }

        chatContainer.revalidate()
        chatContainer.repaint()

        SwingUtilities.invokeLater {
            scrollChatToBottomInUIThread()
        }
    }

    private fun filterToolOutput(text: String): String {
        var filteredText = text

        if (filteredText.contains("## Tool Input")) {
            val toolOutputIndex = filteredText.indexOf("## Tool Output")
            if (toolOutputIndex != -1) {
                filteredText = filteredText.substring(toolOutputIndex)
            }
        }

        val toolsIndex = filteredText.indexOf("You ONLY have access to the following tools")
        if (toolsIndex != -1) {
            filteredText = filteredText.substring(0, toolsIndex).trim()
        }

        return filteredText
    }

    private data class RoleAndMessage(val role: String?, val cleanedMessage: String)

    private fun extractRoleFromMessage(text: String): String? {
        if (text.trim().startsWith("## Tool Output:")) {
            return null
        }

        if (text.contains("# Agent:")) {
            val agentPattern = "# Agent:\\s*([^\\n]+)".toRegex()
            val matchResult = agentPattern.find(text)
            return matchResult?.groupValues?.get(1)?.trim()
        }

        // First try to match predefined roles
        val predefinedRolePattern = "^(Software Engineer|Team Lead|QA Engineer)\\b".toRegex()
        val predefinedMatchResult = predefinedRolePattern.find(text.trim())
        if (predefinedMatchResult != null) {
            return predefinedMatchResult.groupValues[1].trim()
        }

        // If no predefined role matches, try to match any role at the beginning of the message
        val customRolePattern = "^([^\\n:]+?)(?:\\s*:|\\s*$)".toRegex()
        val customMatchResult = customRolePattern.find(text.trim())
        return customMatchResult?.groupValues?.get(1)?.trim()
    }

    private fun extractRoleAndCleanMessage(text: String): RoleAndMessage {
        val role = extractRoleFromMessage(text)
        var cleanedMessage = text

        if (role != null) {
            if (cleanedMessage.contains("# Agent:")) {
                val agentPattern = "# Agent:\\s*([^\\n]+)".toRegex()
                val matchResult = agentPattern.find(cleanedMessage)
                if (matchResult != null) {
                    val fullMatch = matchResult.value
                    cleanedMessage = cleanedMessage.replaceFirst(fullMatch, "").trim()
                }
            } else {
                // First try to match predefined roles
                val predefinedRolePattern = "^(Software Engineer|Team Lead|QA Engineer)\\b".toRegex()
                val predefinedMatchResult = predefinedRolePattern.find(cleanedMessage.trim())
                if (predefinedMatchResult != null) {
                    val fullMatch = predefinedMatchResult.value
                    cleanedMessage = cleanedMessage.replaceFirst(fullMatch, "").trim()
                } else {
                    // If no predefined role matches, try to match any role at the beginning of the message
                    val customRolePattern = "^([^\\n:]+?)(?:\\s*:|\\s*$)".toRegex()
                    val customMatchResult = customRolePattern.find(cleanedMessage.trim())
                    if (customMatchResult != null) {
                        val fullMatch = customMatchResult.value
                        cleanedMessage = cleanedMessage.replaceFirst(fullMatch, "").trim()
                    }
                }
            }
        }

        return RoleAndMessage(role, cleanedMessage)
    }

    private fun addBubble(isUser: Boolean, text: String) {
        val filteredText = if (!isUser) filterToolOutput(text) else text

        var headerText = ""
        var cleanedMessage = filteredText

        if (!isUser) {
            if (text == "Got your request and sending it to the team...") {
                headerText = "Internies"
            }
            else if (filteredText.trim().startsWith("## Tool Output:")) {
                headerText = lastMessageHeader
            }
            else {
                val roleAndMessage = extractRoleAndCleanMessage(filteredText)
                headerText = roleAndMessage.role ?: "Internies"
                cleanedMessage = roleAndMessage.cleanedMessage
            }

            lastMessageHeader = headerText

            if ((filteredText.trim().startsWith("## Tool Output:") || filteredText.trim().startsWith("## Task:"))) {
                return
            }

            val header = JLabel(headerText).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.GRAY
                horizontalAlignment = SwingConstants.RIGHT
                alignmentX = Component.RIGHT_ALIGNMENT
                border = EmptyBorder(0, 0, 2, 0)
            }
            chatContainer.add(header)
        }

        val bubbleContent: JComponent

        if (!isUser && cleanedMessage.contains("```")) {
            val messagePanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = EmptyBorder(6, 8, 6, 8)
            }

            val parts = cleanedMessage.split("```")

            for (i in parts.indices) {
                if (i % 2 == 0) {
                    if (parts[i].isNotEmpty()) {
                        val textArea = JTextArea(parts[i]).apply {
                            isEditable = false
                            isOpaque = false
                            lineWrap = true
                            wrapStyleWord = true
                            border = EmptyBorder(0, 0, 0, 0)
                        }
                        messagePanel.add(textArea)
                    }
                } else {
                    val codeText = parts[i].trim()
                    if (codeText.isNotEmpty()) {
                        val codeArea = JTextArea(codeText).apply {
                            isEditable = false
                            font = Font("Monospaced", Font.PLAIN, 12)
                            background = JBColor(Color(240, 240, 240), Color(50, 50, 50))
                            foreground = JBColor(Color(0, 0, 0), Color(200, 200, 200))
                            lineWrap = true
                            wrapStyleWord = true
                            border = EmptyBorder(8, 8, 8, 8)
                        }
                        val codePanel = JPanel(BorderLayout()).apply {
                            background = codeArea.background
                            border = LineBorder(JBColor.border(), 1, true)
                            add(codeArea, BorderLayout.CENTER)
                        }
                        messagePanel.add(codePanel)
                        messagePanel.add(Box.createVerticalStrut(4))
                    }
                }
            }

            bubbleContent = messagePanel
        } else {
            bubbleContent = JTextArea(cleanedMessage).apply {
                isEditable = false
                isOpaque = false
                lineWrap = true
                wrapStyleWord = true
                border = EmptyBorder(6, 8, 6, 8)
            }
        }

        val bubble = JPanel(BorderLayout()).apply {
            background = if (isUser) JBColor.LIGHT_GRAY else JBColor.PanelBackground
            isOpaque = true
            border = LineBorder(JBColor.border(), 1, true)
            add(bubbleContent, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        chatContainer.add(bubble)
        chatContainer.add(Box.createVerticalStrut(6))
    }

    private fun hyperlink(text: String, onClick: () -> Unit) = JLabel("<html><u>$text</u></html>").apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) = onClick()
        })
    }
}
