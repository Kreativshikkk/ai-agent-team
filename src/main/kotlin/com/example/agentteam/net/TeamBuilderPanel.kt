// src/main/kotlin/com/example/agentteam/net/TeamBuilderPanel.kt
package com.example.agentteam.net

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.JBColor
import com.intellij.ui.content.ContentFactory
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class TeamBuilderPanel(private val project: Project) : ToolWindowFactory {

    // Спиннеры и поля
    private val tlSpin   = JSpinner(SpinnerNumberModel(1, 1, 1, 1)).apply { isEnabled = false }
    private val engSpin  = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val qaSpin   = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val taskArea = JTextArea(3, 60)
    private val rolePrompts = mutableMapOf<String, String>()
    private var firstMessageSent = false

    // Кнопки
    private lateinit var plusButton: JButton
    private lateinit var createBtn:  JButton
    private lateinit var chatButton: JButton

    // Chat UI
    private lateinit var chatContainer: JPanel
    private lateinit var inputField: JTextArea

    // Две панели под CardLayout
    private val rolesPanel = createRolesPanel()
    private val chatPanel  = createChatPanel()
    private val cardPanel  = JPanel(CardLayout()).apply {
        add(rolesPanel, "ROLES")
        add(chatPanel,  "CHAT")
    }
    val component: JPanel = cardPanel

    // ─── Roles Screen ───────────────────────────────────
    private fun createRolesPanel(): JPanel = JPanel(BorderLayout()).apply {
        // ▲ Вверху – кнопка «Chat» (скрыта по умолчанию)
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

        // ⚙️ Список ролей по центру
        val rolesBox = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
            add(roleRow("Team-leads",   tlSpin, "teamLead"));    add(Box.createVerticalStrut(8))
            add(roleRow("Engineers",    engSpin, "engineer"));   add(Box.createVerticalStrut(8))
            add(roleRow("QA Engineers", qaSpin,  "qaEngineer")); add(Box.createVerticalStrut(8))
        }
        add(rolesBox, BorderLayout.CENTER)

        // ➕ и «Create Team» внизу
        plusButton = JButton("+").apply {
            toolTipText = "Добавить новую роль"
            addActionListener {
                // ваш код создания новой роли…
            }
        }
        createBtn = JButton("Create Team").apply {
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

    private fun roleRow(label: String, spinner: JSpinner, key: String): JPanel =
        JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(hyperlink(label) {
                val p = Messages.showInputDialog(
                    project, "Prompt for $label:", "Custom Prompt", null,
                    rolePrompts[key] ?: "", null
                ) ?: return@hyperlink
                rolePrompts[key] = p.trim()
            }, BorderLayout.WEST)
            add(spinner, BorderLayout.EAST)
            maximumSize = preferredSize
        }

    private fun onSubmitRoles() {
        val config = TeamConfig(
            task             = taskArea.text.trim(),
            teamLeads        = 1,
            softwareEngineers= engSpin.value as Int,
            qaEngineers      = qaSpin.value as Int,
            globalPrompt     = "",
            rolePrompts      = rolePrompts.toMap()
        )
        TeamStore.get().add(config)
        try {
            PythonCrewGenerator(project).generateJsonFile(config)
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to generate JSON crew file: ${e.message}",
                "Error"
            )
        }

        chatButton.isVisible = true
        rolesPanel.revalidate()
        rolesPanel.repaint()
        showChatScreen()
    }

    // ─── Chat Screen ───────────────────────────────────
    private fun createChatPanel(): JPanel = JPanel(BorderLayout()).apply {
        // ▲ Вверху – «View Team»
        val viewButton = JButton("View Team").apply {
            toolTipText = "Просмотреть состав команды"
            addActionListener { showReadOnlyRoles() }
        }
        add(
            JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                border = EmptyBorder(4, 8, 4, 8)
                add(viewButton)
            },
            BorderLayout.NORTH
        )

        // ── Разделитель
        add(JSeparator(SwingConstants.HORIZONTAL), BorderLayout.CENTER)

        // 📜 История чата
        chatContainer = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
            add(Box.createVerticalGlue())
            add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    border = EmptyBorder(0, 8, 12, 8)
                    add(
                        JLabel("This is your new chat, here you will communicate with agents")
                            .apply {
                                alignmentX = Component.CENTER_ALIGNMENT
                                font       = font.deriveFont(Font.ITALIC, 12f)
                                foreground = JBColor.GRAY
                            }
                    )
                }
            )
            add(Box.createVerticalGlue())
        }
        val chatScroll = JScrollPane(
            chatContainer,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        ).apply { preferredSize = Dimension(-1, 200) }
        add(chatScroll, BorderLayout.CENTER)

        // 📝 Ввод + Send
        inputField = JTextArea(3, 60).apply {
            lineWrap      = true
            wrapStyleWord = true
        }
        add(
            JPanel(BorderLayout()).apply {
                border = EmptyBorder(8, 8, 8, 8)
                add(
                    JScrollPane(
                        inputField,
                        JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                    ).apply { preferredSize = Dimension(-1, 100) },
                    BorderLayout.CENTER
                )
                add(JButton("Send").apply { addActionListener { onSend() } },
                    BorderLayout.EAST
                )
            },
            BorderLayout.SOUTH
        )
    }

    private fun showChatScreen() {
        inputField.text = ""
        (cardPanel.layout as CardLayout).show(cardPanel, "CHAT")
        cardPanel.revalidate()
        cardPanel.repaint()
    }

    private fun showReadOnlyRoles() {
        (cardPanel.layout as CardLayout).show(cardPanel, "ROLES")
        tlSpin.isEnabled   = false
        engSpin.isEnabled  = false
        qaSpin.isEnabled   = false
        plusButton.isEnabled = false
        createBtn.isEnabled  = false
    }

    private fun stripAnsi(text: String): String =
        text.replace(Regex("\\u001B\\[[;\\d]*m"), "")

    private fun onSend() {
        // 1) Ввод и очистка
        val raw = inputField.text.trim().takeIf(String::isNotEmpty) ?: return
        inputField.text = ""

        // 2) Первый раз убираем пояснение
        if (!firstMessageSent) {
            chatContainer.removeAll()
            firstMessageSent = true
        }

        // 3) Мгновенно своё
        addBubble(isUser = true, text = stripAnsi(raw))
        chatContainer.revalidate()
        chatContainer.repaint()

        // 4) Одно «пузырёк» от агента
        try {
            val pythonScriptPath = "${ConfigUtil.getPythonScriptsPath()}/team.py"
            val process = ProcessBuilder("python3.11", pythonScriptPath)
                .redirectErrorStream(true)
                .start()

            Thread {
                val output = process.inputStream.readBytes()
                    .toString(Charsets.UTF_8)
                    .trim()
                SwingUtilities.invokeLater {
                    addBubble(isUser = false, text = stripAnsi(output))
                    val vsb = (chatContainer.parent as JScrollPane).verticalScrollBar
                    vsb.value = vsb.maximum
                }
            }.start()

        } catch (e: Exception) {
            SwingUtilities.invokeLater {
                addBubble(isUser = false, text = "Error: ${e.message}")
            }
        }
    }

    private fun addBubble(isUser: Boolean, text: String) {
        if (!isUser) {
            val hdr = JLabel("Internies").apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.GRAY
                horizontalAlignment = SwingConstants.RIGHT
                alignmentX = Component.RIGHT_ALIGNMENT
                border = EmptyBorder(0, 0, 2, 0)
            }
            chatContainer.add(hdr)
        }
        val textArea = JTextArea(text).apply {
            isEditable    = false
            isOpaque      = false
            lineWrap      = true
            wrapStyleWord = true
            border        = EmptyBorder(6, 8, 6, 8)
            maximumSize   = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        val bubble = JPanel(BorderLayout()).apply {
            background  = if (isUser) JBColor.LIGHT_GRAY else JBColor.PanelBackground
            isOpaque    = true
            border      = LineBorder(JBColor.border(), 1, true)
            add(textArea, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            alignmentX  = if (isUser) Component.LEFT_ALIGNMENT else Component.RIGHT_ALIGNMENT
        }
        chatContainer.add(bubble)
        chatContainer.add(Box.createVerticalStrut(6))
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TeamBuilderPanel(project)
        val content = ContentFactory.getInstance()
            .createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)

        val twEx = toolWindow as ToolWindowEx
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
        twEx.setTitleActions(viewAction, chatAction)
    }

    override fun shouldBeAvailable(project: Project) = true
}
