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
    // Track the last message header
    private var lastMessageHeader = "Internies"

    // Helper function to strip ANSI color codes
    private fun stripAnsiCodes(text: String): String {
        val ansiPattern = Pattern.compile("\u001B\\[[;\\d]*m")
        return ansiPattern.matcher(text).replaceAll("")
    }

    // ─── widgets & state ─────────────────────────
    private val tlSpin = JSpinner(SpinnerNumberModel(1, 1, 1, 1)).apply { isEnabled = false }
    private val engSpin = JSpinner(SpinnerNumberModel(1, 0, 99, 1))
    private val qaSpin = JSpinner(SpinnerNumberModel(1, 0, 99, 1))
    private val taskArea = JTextArea(3, 60)
    private val rolePrompts = mutableMapOf<String, String>()
    private var firstMessageSent = false

    // Buttons
    private lateinit var plusButton: JButton
    private lateinit var createBtn: JButton
    private lateinit var chatButton: JButton

    // chat state
    private lateinit var chatContainer: JPanel
    private lateinit var inputField: JTextArea

    // cards
    private val rolesPanel = createRolesPanel()
    private val chatPanel = createChatPanel()
    private val cardPanel = JPanel(CardLayout()).apply {
        add(rolesPanel, "ROLES")
        add(chatPanel, "CHAT")
    }

    val component: JPanel = cardPanel

    // ─── Roles screen ─────────────────────────────
    private fun createRolesPanel(): JPanel = JPanel(BorderLayout()).apply {
        // ▲ At the top - "Chat" button (hidden by default)
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

        // Banner or spacer
        val icon = IconLoader.getIcon("/icons/banner.png", TeamBuilderPanel::class.java)
        val bannerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(JLabel(icon))
            // Set strictly to the size of the image
            preferredSize = Dimension(icon.iconWidth, icon.iconHeight)
            maximumSize = preferredSize
        }

        val welcomeLabel = JLabel("Welcome! Let’s Kickstart Your Agent Team!").apply {
            horizontalAlignment = SwingConstants.CENTER       // текст по центру
            alignmentX = Component.CENTER_ALIGNMENT           // тоже центр в BoxLayout
            font = font.deriveFont(Font.BOLD, 16f)             // чуть побольше и жирнее
            foreground = JBColor.foreground()                  // цвет в соответствии с темой
            border = EmptyBorder(10, 0, 10, 0)                  // 8px сверху и снизу от картинки
        }

        val centerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(bannerPanel)
            add(welcomeLabel)                                 // теперь сразу под картинкой
        }
        add(centerPanel, BorderLayout.CENTER)

        val rolesBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
            // начальные строки
            add(roleRow("Team-leads", tlSpin, "teamLead")); add(Box.createVerticalStrut(8))
            add(roleRow("Software Engineers", engSpin, "engineer")); add(Box.createVerticalStrut(8))
            add(roleRow("QA Engineers", qaSpin, "qaEngineer")); add(Box.createVerticalStrut(8))
        }
        add(rolesBox, BorderLayout.CENTER)

        // "+" button
        plusButton = JButton("+").apply {
            toolTipText = "Add new role"
            addActionListener {
                // --- building the form ---
                val roleField     = JTextField()
                val goalField     = JTextField()
                val backstoryArea = JTextArea(3, 20).apply {
                    lineWrap      = true
                    wrapStyleWord = true
                }
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
                }

                // --- показываем форму через DialogBuilder ---
                val builder = com.intellij.openapi.ui.DialogBuilder(project).apply {
                    setTitle("Create New Role")
                    setCenterPanel(form)
                    removeAllActions()
                    addOkAction()
                    addCancelAction()
                }
                if (builder.show() != com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE) return@addActionListener

                // --- читаем значения и добавляем новую роль ---
                val role  = roleField.text.trim().takeIf(String::isNotEmpty) ?: return@addActionListener
                val goal      = goalField.text.trim()
                val backstory = backstoryArea.text.trim()

                val spinner = JSpinner(SpinnerNumberModel(1, 0, 99, 1))
                val key     = role.replace("\\s+".toRegex(), "").replaceFirstChar { it.lowercase() }
                rolePrompts[key] = """
      role: $role
      goal: $goal
      backstory: $backstory
    """.trimIndent()

                rolesBox.add(roleRow(role, spinner, key))
                rolesBox.add(Box.createVerticalStrut(8))
                rolesBox.revalidate()
                rolesBox.repaint()
            }
        }

        // now forming the south part: first "+", then Create Team
        createBtn = JButton("Create Team").apply {
            font = font.deriveFont(Font.BOLD, 16f)
            preferredSize = Dimension(-1, 100)
            addActionListener {
                onSubmitRoles()      // ← your method is called here
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
        add(hyperlink(label) {
            val p = Messages.showInputDialog(
                project, "Prompt for $label:", "Custom Prompt", null, rolePrompts[key] ?: "", null
            ) ?: return@hyperlink
            rolePrompts[key] = p.trim()
        }, BorderLayout.WEST)
        add(spinner, BorderLayout.EAST)
        maximumSize = preferredSize
    }

    private fun onSubmitRoles() {
        val config = TeamConfig(
            task = taskArea.text.trim(),
            teamLeads = tlSpin.value as Int,
            techLeads = 0, // Tech leads option removed
            engineers = engSpin.value as Int,
            qaEngineers = qaSpin.value as Int,
            globalPrompt = "",
            rolePrompts = rolePrompts.toMap()
        )
        TeamStore.get().add(config)

        // Generate JSON crew file
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

    // ─── Chat screen ─────────────────────────────
    private fun createChatPanel(): JPanel = JPanel(BorderLayout()).apply {
        // ▲ At the top - "View Team"
        val viewButton = JButton("View Team").apply {
            toolTipText = "View team composition"
            addActionListener { showReadOnlyRoles() }
        }

        // Create a panel for the top section with the button and separator
        val topPanel = JPanel(BorderLayout())
        topPanel.add(
            JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                border = EmptyBorder(4, 8, 4, 8)
                add(viewButton)
            },
            BorderLayout.NORTH
        )

        // ── Separator
        topPanel.add(JSeparator(SwingConstants.HORIZONTAL), BorderLayout.SOUTH)

        // Add the top panel to the main panel
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
        // 3) уже потом скролл с сообщениями
        val chatScroll = JScrollPane(
            chatContainer, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
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
        // Get the latest team configuration
        val configs = TeamStore.get().all()
        if (configs.isNotEmpty()) {
            val latestConfig = configs.last()

            // Update the UI fields with the values from the config
            tlSpin.value = latestConfig.teamLeads
            engSpin.value = latestConfig.engineers
            qaSpin.value = latestConfig.qaEngineers
            taskArea.text = latestConfig.task

            // Update role prompts
            rolePrompts.clear()
            rolePrompts.putAll(latestConfig.rolePrompts)
        }

        // Switch to the ROLES panel and disable all inputs
        (cardPanel.layout as CardLayout).show(cardPanel, "ROLES")
        tlSpin.isEnabled = false
        engSpin.isEnabled = false
        qaSpin.isEnabled = false
        plusButton.isEnabled = false
        createBtn.isEnabled = false
        chatButton.isVisible = true

        // Update the UI
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

        // при первом сообщении удаляем explanatory-блок и glues
        if (!firstMessageSent) {
            chatContainer.removeAll()
            firstMessageSent = true
        }

        // добавляем bubble, как обычно
        addBubble(isUser = true, text = txt)

        // Add confirmation message before executing the Python script
        addBubble(isUser = false, text = "Got your request and sending it to the team...")
        chatContainer.revalidate()
        chatContainer.repaint()

        // Scroll to show the confirmation message
        scrollChatToBottom()

        // Note: We now generate a JSON file instead of a Python script
        // This execution logic might need to be updated in the future
        try {
            val pythonScriptPath = "${ConfigUtil.getPythonScriptsPath()}/team.py"
            // Pass the user input as a command-line argument to the Python script
            val process = ProcessBuilder("python3.11", pythonScriptPath, txt).redirectErrorStream(true).start()

            // Start a thread to read the process output incrementally
            Thread {
                try {
                    val reader = process.inputStream.bufferedReader()
                    var collectingMessage = false
                    var currentMessage = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.contains("# Agent:")) {
                            // Start collecting a new message
                            collectingMessage = true

                            // If we already have a message buffer, display it first
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

                            // Extract the content after "# Agent:" and strip ANSI codes
                            val cleanLine = stripAnsiCodes(line!!)
                            val agentPart = cleanLine.substringAfter("# Agent:")

                            // Check if there's a robot emoji on the same line
                            if (agentPart.contains("🤖")) {
                                // Extract content between "# Agent:" and the emoji
                                val beforeEmoji = agentPart.substringBefore("🤖")
                                if (beforeEmoji.isNotEmpty()) {
                                    // Skip if the line contains crew information
                                    if (!beforeEmoji.contains("🚀 Crew:") && 
                                        !beforeEmoji.contains("├──") && 
                                        !beforeEmoji.contains("│") && 
                                        !beforeEmoji.contains("└──")) {
                                        currentMessage.append(beforeEmoji)
                                    }
                                }

                                // Display the collected message
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

                                // Reset for the next message
                                collectingMessage = false
                                currentMessage = StringBuilder()
                            } else {
                                // No emoji on this line, continue collecting
                                // Format as "# Agent: Team Lead" followed by the task description
                                if (agentPart.trim().isNotEmpty()) {
                                    // Skip if the line contains crew information
                                    if (!agentPart.contains("🚀 Crew:") && 
                                        !agentPart.contains("├──") && 
                                        !agentPart.contains("│") && 
                                        !agentPart.contains("└──")) {
                                        currentMessage.append(agentPart).append("\n")
                                    }
                                }
                            }
                        } else if (collectingMessage) {
                            // If we're collecting a message and encounter the robot emoji, stop collecting
                            if (line!!.contains("🤖")) {
                                // Extract content before the emoji
                                val beforeEmoji = line!!.substringBefore("🤖")
                                if (beforeEmoji.isNotEmpty()) {
                                    // Skip if the line contains crew information
                                    if (!beforeEmoji.contains("🚀 Crew:") && 
                                        !beforeEmoji.contains("├──") && 
                                        !beforeEmoji.contains("│") && 
                                        !beforeEmoji.contains("└──")) {
                                        currentMessage.append(beforeEmoji)
                                    }
                                }

                                // Display the collected message
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

                                // Reset for the next message
                                collectingMessage = false
                                currentMessage = StringBuilder()
                            } else {
                                // Skip lines containing crew information
                                if (!line!!.startsWith("🚀 Crew:") && 
                                    !line!!.startsWith("├──") && 
                                    !line!!.startsWith("│") && 
                                    !line!!.startsWith("└──")) {
                                    // Continue collecting the message
                                    currentMessage.append(line).append("\n")
                                }
                            }
                        }
                        // If not collecting a message, ignore the line
                    }

                    // Display any remaining content if we were collecting a message
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

        // No need for the echo message anymore
        // chatContainer.revalidate(); chatContainer.repaint()
        // addBubble(isUser = false, text = "Echo: $txt")

        chatContainer.revalidate()
        chatContainer.repaint()

        SwingUtilities.invokeLater {
            scrollChatToBottomInUIThread()
        }
    }

    // Helper function to filter out content before "## Tool Output" if "## Tool Input" is present
    // and to filter out content starting from "You ONLY have access to the following tools" if present
    private fun filterToolOutput(text: String): String {
        var filteredText = text

        // Filter out content before "## Tool Output" if "## Tool Input" is present
        if (filteredText.contains("## Tool Input")) {
            val toolOutputIndex = filteredText.indexOf("## Tool Output")
            if (toolOutputIndex != -1) {
                filteredText = filteredText.substring(toolOutputIndex)
            }
        }

        // Filter out content starting from "You ONLY have access to the following tools" if present
        val toolsIndex = filteredText.indexOf("You ONLY have access to the following tools")
        if (toolsIndex != -1) {
            filteredText = filteredText.substring(0, toolsIndex).trim()
        }

        return filteredText
    }

    // Data class to hold both the role and the cleaned message
    private data class RoleAndMessage(val role: String?, val cleanedMessage: String)

    // Helper function to extract role from messages that start with a role name
    private fun extractRoleFromMessage(text: String): String? {
        // Check if the message starts with "## Tool Output:"
        if (text.trim().startsWith("## Tool Output:")) {
            // Use the previous message's header
            return null
        }

        // Check if the message starts with "# Agent:"
        if (text.contains("# Agent:")) {
            val agentPattern = "# Agent:\\s*([^\\n]+)".toRegex()
            val matchResult = agentPattern.find(text)
            return matchResult?.groupValues?.get(1)?.trim()
        }

        // Check if the message starts with a role name (e.g., "Software Engineer")
        val rolePattern = "^(Software Engineer|Team Lead|QA Engineer)\\b".toRegex()
        val matchResult = rolePattern.find(text.trim())
        return matchResult?.groupValues?.get(1)?.trim()
    }

    // Helper function to extract role and clean message content
    private fun extractRoleAndCleanMessage(text: String): RoleAndMessage {
        val role = extractRoleFromMessage(text)
        var cleanedMessage = text

        // Clean the message by removing the role prefix
        if (role != null) {
            // If the message contains "# Agent:", remove that part
            if (cleanedMessage.contains("# Agent:")) {
                val agentPattern = "# Agent:\\s*([^\\n]+)".toRegex()
                val matchResult = agentPattern.find(cleanedMessage)
                if (matchResult != null) {
                    val fullMatch = matchResult.value
                    cleanedMessage = cleanedMessage.replaceFirst(fullMatch, "").trim()
                }
            } else {
                // If the message starts with a role name, remove it
                val rolePattern = "^(Software Engineer|Team Lead|QA Engineer)\\b".toRegex()
                val matchResult = rolePattern.find(cleanedMessage.trim())
                if (matchResult != null) {
                    val fullMatch = matchResult.value
                    cleanedMessage = cleanedMessage.replaceFirst(fullMatch, "").trim()
                }
            }
        }

        return RoleAndMessage(role, cleanedMessage)
    }

    private fun addBubble(isUser: Boolean, text: String) {
        // Filter the text if it's not from the user
        val filteredText = if (!isUser) filterToolOutput(text) else text

        // Variables to hold the header text and the cleaned message
        var headerText = ""
        var cleanedMessage = filteredText

        if (!isUser) {
            // For the confirmation message, use the agent's name and don't clean the message
            if (text == "Got your request and sending it to the team...") {
                headerText = "Internies"
            }
            // For messages starting with "## Tool Output:", use the previous header and don't clean the message
            else if (filteredText.trim().startsWith("## Tool Output:")) {
                headerText = lastMessageHeader
            }
            // For other messages, extract the role and clean the message
            else {
                val roleAndMessage = extractRoleAndCleanMessage(filteredText)
                headerText = roleAndMessage.role ?: "Internies"
                cleanedMessage = roleAndMessage.cleanedMessage
            }

            // Update the last message header
            lastMessageHeader = headerText

            val header = JLabel(headerText).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.GRAY
                horizontalAlignment = SwingConstants.RIGHT
                alignmentX = Component.RIGHT_ALIGNMENT
                border = EmptyBorder(0, 0, 2, 0)
            }
            chatContainer.add(header)
        }
        val bubbleText = JTextArea(cleanedMessage).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            border = EmptyBorder(6, 8, 6, 8)
        }

        val bubble = JPanel(BorderLayout()).apply {
            background = if (isUser) JBColor.LIGHT_GRAY else JBColor.PanelBackground
            isOpaque = true
            border = LineBorder(JBColor.border(), 1, true)
            add(bubbleText, BorderLayout.CENTER)
            // Ставим фиксированную ширину (высота пересчитается автоматически)
            maximumSize = Dimension(300, Int.MAX_VALUE)
            alignmentX = if (isUser) Component.LEFT_ALIGNMENT else Component.RIGHT_ALIGNMENT
        }
        chatContainer.add(bubble)
        chatContainer.add(Box.createVerticalStrut(6))
    }

    // --- helper ---
    private fun hyperlink(text: String, onClick: () -> Unit) = JLabel("<html><u>$text</u></html>").apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) = onClick()
        })
    }
}
