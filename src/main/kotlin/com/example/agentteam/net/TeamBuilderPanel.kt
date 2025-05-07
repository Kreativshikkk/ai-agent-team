package com.example.agentteam.net
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import com.example.agentteam.net.PythonCrewGenerator
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

private data class Msg(val isUser: Boolean, val text: String)

class TeamBuilderPanel(private val project: Project) {

    // ─── widgets & state ─────────────────────────
    private val tlSpin   = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val techSpin = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val engSpin  = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val qaSpin   = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val taskArea = JTextArea(3, 60)
    private val rolePrompts = mutableMapOf<String, String>()
    private var firstMessageSent = false

    // chat state
    private lateinit var chatContainer: JPanel
    private lateinit var inputField: JTextArea

    // cards
    private val rolesPanel = createRolesPanel()
    private val chatPanel  = createChatPanel()
    private val cardPanel  = JPanel(CardLayout()).apply {
        add(rolesPanel, "ROLES")
        add(chatPanel,  "CHAT")
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
            maximumSize   = preferredSize
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
            add(roleRow("Team-leads", tlSpin, "teamLead"));   add(Box.createVerticalStrut(8))
            add(roleRow("Tech-leads", techSpin, "techLead")); add(Box.createVerticalStrut(8))
            add(roleRow("Engineers",  engSpin, "engineer"));  add(Box.createVerticalStrut(8))
            add(roleRow("QA Engineers", qaSpin, "qaEngineer")); add(Box.createVerticalStrut(8))
        }
        add(rolesBox, BorderLayout.CENTER)

        // кнопка "+"
        val plusButton = JButton("+").apply {
            toolTipText = "Добавить новую роль"
            addActionListener {
                // 1) спросить у пользователя имя роли
                val roleName = Messages.showInputDialog(
                    project,
                    "Введите название новой роли:",
                    "Добавить роль",
                    null
                )?.trim().takeIf { it?.isNotEmpty() == true } ?: return@addActionListener

                // 2) спросить prompt
                val prompt = Messages.showInputDialog(
                    project,
                    "Введите prompt для \"$roleName\":",
                    "Prompt новой роли",
                    null
                )?.trim().orEmpty()

                // 3) создаём спиннер и ключ
                val spinner = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
                val key = roleName.replace("\\s+".toRegex(), "").decapitalize()

                // 4) сохраняем prompt
                rolePrompts[key] = prompt

                // 5) добавляем новую строку в контейнер и обновляем UI
                rolesBox.add(roleRow(roleName, spinner, key))
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
            },
            BorderLayout.SOUTH
        )
    }

    private fun roleRow(label: String, spinner: JSpinner, key: String): JPanel =
        JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(hyperlink(label) {
                val p = Messages.showInputDialog(
                    project, "Prompt for $label:", "Role Prompt",
                    null, rolePrompts[key] ?: "", null
                ) ?: return@hyperlink
                rolePrompts[key] = p.trim()
            }, BorderLayout.WEST)
            add(spinner, BorderLayout.EAST)
            maximumSize = preferredSize
        }

    private fun onSubmitRoles() {
        val config = TeamConfig(
            task         = taskArea.text.trim(),
            teamLeads    = tlSpin.value as Int,
            techLeads    = techSpin.value as Int,
            engineers    = engSpin.value as Int,
            qaEngineers  = qaSpin.value as Int,
            globalPrompt = "",
            rolePrompts  = rolePrompts.toMap()
        )
        TeamStore.get().add(config)

        // Generate JSON crew file
        try {
            val crewGenerator = PythonCrewGenerator(project)
            crewGenerator.generateJsonFile(config)
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to generate JSON crew file: ${e.message}", "Agent Team Builder")
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
            chatContainer,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        ).apply { preferredSize = Dimension(-1, 100) }
        add(chatScroll, BorderLayout.CENTER)
        inputField = JTextArea(3, 60)
        add(JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 8, 8)
            val inputScroll = JScrollPane(
                inputField,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ).apply { preferredSize = Dimension(-1, 100) }
            add(inputScroll, BorderLayout.CENTER)
            add(JButton("Send").apply { addActionListener { onSend() } },
                BorderLayout.EAST)
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
        addBubble(isUser = true,  text = txt)

        // Note: We now generate a JSON file instead of a Python script
        // This execution logic might need to be updated in the future
        try {
            val process = ProcessBuilder("python3", "/Users/iya/jb/hackathon-25/ai-agent-team/src/main/python/crew_ai_team.py")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            addBubble(isUser = false, text = output.ifEmpty { "Script executed with no output" })
        } catch (e: Exception) {
            addBubble(isUser = false, text = "Error running script: ${e.message}\nNote: A JSON file has been generated at /Users/iya/jb/hackathon-25/ai-agent-team/src/main/python/crew.json")
        }

        chatContainer.revalidate(); chatContainer.repaint()
        addBubble(isUser = false, text = "Echo: $txt")

        chatContainer.revalidate()
        chatContainer.repaint()

        SwingUtilities.invokeLater {
            val vsb = (chatContainer.parent as JScrollPane).verticalScrollBar
            vsb.value = vsb.maximum
        }
    }

    private fun addBubble(isUser: Boolean, text: String) {
        if (!isUser) {
            val header = JLabel("Agent Team Builder").apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.GRAY
                horizontalAlignment = SwingConstants.RIGHT
                alignmentX = Component.RIGHT_ALIGNMENT
                border = EmptyBorder(0, 0, 2, 0)
            }
            chatContainer.add(header)
        }
        val bubble = JPanel(BorderLayout()).apply {
            background = if (isUser) JBColor.LIGHT_GRAY else JBColor.PanelBackground
            isOpaque = true
            border = LineBorder(JBColor.border(), 1, true)
            val lbl = JLabel("<html>${text.replace("\n","<br>")}</html>").apply {
                border = EmptyBorder(6, 8, 6, 8)
            }
            add(lbl, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            alignmentX = if (isUser) Component.LEFT_ALIGNMENT else Component.RIGHT_ALIGNMENT
        }
        chatContainer.add(bubble)
        chatContainer.add(Box.createVerticalStrut(6))
    }

    // --- helper ---
    private fun hyperlink(text: String, onClick: () -> Unit) =
        JLabel("<html><u>$text</u></html>").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent?) = onClick()
            })
        }
}
