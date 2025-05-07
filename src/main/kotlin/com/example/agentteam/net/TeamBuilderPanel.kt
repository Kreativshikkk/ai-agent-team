package com.example.agentteam.net

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

private data class Msg(val isUser: Boolean, val text: String)

class TeamBuilderPanel(private val project: Project) {

    // ─── widgets & state ─────────────────────────
    private val tlSpin = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val techSpin = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val engSpin = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val qaSpin = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val taskArea = JTextArea(3, 60)
    private val rolePrompts = mutableMapOf<String, String>()
    private var firstMessageSent = false

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
        // Banner or spacer
        val icon = IconLoader.getIcon("/icons/banner.png", TeamBuilderPanel::class.java)
        val bannerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            add(JLabel(icon))
            // Устанавливаем строго под размер картинки
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

// если вы оборачиваете баннер в northPanel:
        val northPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(bannerPanel)
            add(welcomeLabel)                                 // теперь сразу под картинкой
        }
        add(northPanel, BorderLayout.NORTH)

        val rolesBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
            // начальные строки
            add(roleRow("Team-leads", tlSpin, "teamLead")); add(Box.createVerticalStrut(8))
            add(roleRow("Tech-leads", techSpin, "techLead")); add(Box.createVerticalStrut(8))
            add(roleRow("Engineers", engSpin, "engineer")); add(Box.createVerticalStrut(8))
            add(roleRow("QA Engineers", qaSpin, "qaEngineer")); add(Box.createVerticalStrut(8))
        }
        add(rolesBox, BorderLayout.CENTER)

        // кнопка "+"
        val plusButton = JButton("+").apply {
            toolTipText = "Добавить новую роль"
            addActionListener {
                // --- строим форму ---
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

                val spinner = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
                val key     = role.replace("\\s+".toRegex(), "").decapitalize()
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

        // теперь формируем юг: сначала "+", потом Create Team
        val createBtn = JButton("Create Team").apply {
            font = font.deriveFont(Font.BOLD, 16f)
            preferredSize = Dimension(-1, 100)
            addActionListener {
                onSubmitRoles()      // ← здесь и вызывается ваш метод
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
            techLeads = techSpin.value as Int,
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
            Messages.showErrorDialog(project, "Failed to generate JSON crew file: ${e.message}", "Internie")
        }

        showChatScreen()
    }

    // ─── Chat screen ─────────────────────────────
    private fun createChatPanel(): JPanel = JPanel(BorderLayout()).apply {
        add(JSeparator(SwingConstants.HORIZONTAL), BorderLayout.NORTH)
        chatContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
        }
        val infoPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(0, 8, 12, 8)
            add(JLabel("Это — ваш новый чат, здесь вы будете общаться с агентами").apply {
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

        // Note: We now generate a JSON file instead of a Python script
        // This execution logic might need to be updated in the future
        try {
            val pythonScriptPath = "${ConfigUtil.getPythonScriptsPath()}/team.py"
            val process = ProcessBuilder("python3.11", pythonScriptPath).redirectErrorStream(true).start()

            // Start a thread to read the process output incrementally
            Thread {
                try {
                    val reader = process.inputStream.bufferedReader()
                    var currentMessage = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.contains("# Agent:")) {
                            // If we have a current message buffer, display it
                            if (currentMessage.isNotEmpty()) {
                                val messageText = currentMessage.toString().trim()
                                SwingUtilities.invokeLater {
                                    addBubble(isUser = false, text = messageText)
                                    chatContainer.revalidate()
                                    chatContainer.repaint()
                                    val vsb = (chatContainer.parent as JScrollPane).verticalScrollBar
                                    vsb.value = vsb.maximum
                                }
                                currentMessage = StringBuilder()
                            }

                            // Start a new message with the current line
                            currentMessage.append(line).append("\n")
                        } else {
                            // Add to the current message buffer
                            currentMessage.append(line).append("\n")
                        }
                    }

                    // Display any remaining content
                    if (currentMessage.isNotEmpty()) {
                        val messageText = currentMessage.toString().trim()
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
            val vsb = (chatContainer.parent as JScrollPane).verticalScrollBar
            vsb.value = vsb.maximum
        }
    }

    private fun addBubble(isUser: Boolean, text: String) {
        if (!isUser) {
            val header = JLabel("Internie").apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.GRAY
                horizontalAlignment = SwingConstants.RIGHT
                alignmentX = Component.RIGHT_ALIGNMENT
                border = EmptyBorder(0, 0, 2, 0)
            }
            chatContainer.add(header)
        }
        val bubbleText = JTextArea(text).apply {
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
