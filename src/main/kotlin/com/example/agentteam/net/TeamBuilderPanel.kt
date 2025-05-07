package com.example.agentteam.net

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.JBColor
import com.intellij.ui.content.ContentFactory
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class TeamBuilderPanel(private val project: Project): ToolWindowFactory {

    private val tlSpin = JSpinner(SpinnerNumberModel(1, 1, 1, 1)).apply {
        value = 1
        isEnabled = false
    }
    private val engSpin = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val qaSpin = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val taskArea = JTextArea(3, 60)
    private val rolePrompts = mutableMapOf<String, String>()
    private var firstMessageSent = false
    private lateinit var plusButton: JButton
    private lateinit var createBtn:  JButton
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

    private fun createRolesPanel(): JPanel = JPanel(BorderLayout()).apply {
        // → Вверху – кнопка «Chat»
        chatButton = JButton("Chat").apply {
            toolTipText = "Вернуться в чат"
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

        // → Центр – список ролей
        val rolesBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
            add(roleRow("Team-leads",  tlSpin,  "teamLead"));    add(Box.createVerticalStrut(8))
            add(roleRow("Engineers",   engSpin,  "engineer"));   add(Box.createVerticalStrut(8))
            add(roleRow("QA Engineers",qaSpin,   "qaEngineer")); add(Box.createVerticalStrut(8))
        }
        add(rolesBox, BorderLayout.CENTER)

        // → Внизу – сначала «+», потом «Create Team»
        val plusButton = JButton("+").apply {
            toolTipText = "Добавить новую роль"
            addActionListener {
                // … код создания новой роли …
            }
        }
        val createBtn = JButton("Create Team").apply {
            font = font.deriveFont(Font.BOLD, 16f)
            preferredSize = Dimension(-1, 100)
            addActionListener { onSubmitRoles() }
        }

        add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(
                    JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                        border = EmptyBorder(0, 8, 4, 8)
                        add(plusButton)
                    }
                )
                add(
                    JPanel(BorderLayout()).apply {
                        border = EmptyBorder(4, 8, 8, 8)
                        add(createBtn, BorderLayout.CENTER)
                    }
                )
            },
            BorderLayout.SOUTH
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
            teamLeads = 1,
            softwareEngineers = engSpin.value as Int,
            qaEngineers = qaSpin.value as Int,
            globalPrompt = "",
            rolePrompts = rolePrompts.toMap()
        )
        TeamStore.get().add(config)

        try {
            val crewGenerator = PythonCrewGenerator(project)
            crewGenerator.generateJsonFile(config)
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to generate JSON crew file: ${e.message}", "Internies")
        }

        chatButton.isVisible = true
        // пересобираем роли-панель, чтобы она перерисовалась
        rolesPanel.revalidate()
        rolesPanel.repaint()

        // возвращаемся в чат
        showChatScreen()
    }

    private fun createChatPanel(): JPanel = JPanel(BorderLayout()).apply {
        // → Вверху – кнопка «View Team»
        val viewButton = JButton("View Team").apply {
            toolTipText = "Просмотреть состав команды"
            addActionListener { showReadOnlyRoles() }
        }
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(4, 8, 4, 8)

            // 1) кнопка справа
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                add(viewButton)
            })

            // 2) сразу под ней тонкая разделительная линия
            add(JSeparator(SwingConstants.HORIZONTAL))
        }

// единоразово вешаем headerPanel на NORTH
        add(headerPanel, BorderLayout.NORTH)

        // → Центр – история и пояснение по центру
        chatContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
            add(Box.createVerticalGlue())
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    border = EmptyBorder(0, 8, 12, 8)
                    add(
                        JLabel("This is your new chat, here you will communicate with agents").apply {
                            alignmentX = Component.CENTER_ALIGNMENT
                            font = font.deriveFont(Font.ITALIC, 12f)
                            foreground = JBColor.GRAY
                        }
                    )
                }
            )
            add(Box.createVerticalGlue())
        }
        val chatScroll = JScrollPane(
            chatContainer,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        ).apply { preferredSize = Dimension(-1, 200) }
        add(chatScroll, BorderLayout.CENTER)

        // → Внизу – поле ввода + Send
        inputField = JTextArea(3, 60).apply {
            lineWrap = true
            wrapStyleWord = true
        }
        add(
            JPanel(BorderLayout()).apply {
                border = EmptyBorder(8, 8, 8, 8)
                add(
                    JScrollPane(
                        inputField,
                        ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                    ).apply { preferredSize = Dimension(-1, 100) },
                    BorderLayout.CENTER
                )
                add(JButton("Send").apply { addActionListener { onSend() } }, BorderLayout.EAST)
            },
            BorderLayout.SOUTH
        )
    }



    private fun showChatScreen() {
        inputField.text = ""
        (cardPanel.layout as CardLayout).show(cardPanel, "CHAT")
        cardPanel.revalidate(); cardPanel.repaint()
    }

    private fun onSend() {
        val txt = inputField.text.trim().takeIf(String::isNotEmpty) ?: return
        inputField.text = ""

        if (!firstMessageSent) {
            chatContainer.removeAll()
            firstMessageSent = true
        }

        addBubble(isUser = true, text = txt)

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
            val header = JLabel("Internies").apply {
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

    private fun hyperlink(text: String, onClick: () -> Unit) = JLabel("<html><u>$text</u></html>").apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) = onClick()
        })
    }

    private fun showReadOnlyRoles() {
        (cardPanel.layout as CardLayout).show(cardPanel, "ROLES")
        tlSpin.isEnabled   = false
        engSpin.isEnabled  = false
        qaSpin.isEnabled   = false
        plusButton.isEnabled = false
        createBtn.isEnabled  = false
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TeamBuilderPanel(project)
        val content = ContentFactory.getInstance()
            .createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)

        val twEx = toolWindow as ToolWindowEx

        // создаём экшны с иконками
        val viewAction = object : AnAction("View Team", "Просмотреть состав команды", AllIcons.Actions.Preview) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.showReadOnlyRoles()
            }
        }
        val chatAction = object : AnAction("Chat", "Вернуться в чат", AllIcons.Actions.Forward) {
            override fun actionPerformed(e: AnActionEvent) {
                panel.showChatScreen()
            }
        }

        // навешиваем их в заголовок
        twEx.setTitleActions(viewAction, chatAction)
    }

    override fun shouldBeAvailable(project: Project) = true

}
