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

private data class Msg(val isUser: Boolean, val text: String)

class TeamBuilderPanel(private val project: Project) {

    // ─── widgets & state ─────────────────────────
    private val tlSpin   = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val techSpin = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val engSpin  = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val qaSpin   = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val taskArea = JTextArea(3, 60)
    private val rolePrompts = mutableMapOf<String, String>()

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
        add(
            try {
                JLabel(IconLoader.getIcon("/icons/banner.png", javaClass))
            } catch (_: Exception) {
                JLabel().apply { preferredSize = Dimension(1, 80) }
            },
            BorderLayout.NORTH
        )

        // roles center
        add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
            add(Box.createVerticalGlue())
            add(roleRow("Team-leads",  tlSpin,  "teamLead"));   add(Box.createVerticalGlue())
            add(roleRow("Tech-leads",  techSpin, "techLead"));  add(Box.createVerticalGlue())
            add(roleRow("Engineers",   engSpin,  "engineer"));  add(Box.createVerticalGlue())
            add(roleRow("QA Engineers",qaSpin,   "qaEngineer"));add(Box.createVerticalGlue())
        }, BorderLayout.CENTER)

        // Task + Submit bottom
        add(JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 8, 8)
            // task field
            val taskScroll = JScrollPane(
                taskArea,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ).apply {
                preferredSize = Dimension(-1, 100)
            }
            add(taskScroll, BorderLayout.CENTER)
            add(JButton("Submit").apply {
                addActionListener { onSubmitRoles() }
            }, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
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
        if (taskArea.text.isBlank()) {
            Messages.showErrorDialog(project, "Task cannot be empty", "Agent Team Builder")
            return
        }
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

        // Generate Python crew file
        try {
            val pythonGenerator = PythonCrewGenerator(project)
            pythonGenerator.generatePythonFile(config)
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to generate Python crew file: ${e.message}", "Agent Team Builder")
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
        val chatScroll = JScrollPane(
            chatContainer,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        ).apply {
            preferredSize = Dimension(-1, 100)
        }
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
        chatContainer.removeAll()
        inputField.text = ""
        (cardPanel.layout as CardLayout).show(cardPanel, "CHAT")
        cardPanel.revalidate(); cardPanel.repaint()
    }

    private fun onSend() {
        val txt = inputField.text.trim().takeIf(String::isNotEmpty) ?: return
        inputField.text = ""
        addBubble(isUser = true,  text = txt)
        addBubble(isUser = false, text = "Echo: $txt")
        chatContainer.revalidate(); chatContainer.repaint()
        SwingUtilities.invokeLater {
            (chatContainer.parent as JScrollPane).verticalScrollBar.value =
                (chatContainer.parent as JScrollPane).verticalScrollBar.maximum
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
