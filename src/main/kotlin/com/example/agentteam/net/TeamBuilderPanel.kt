package com.example.agentteam.net
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.panel
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

class TeamBuilderPanel(private val project: Project) {

    /* роли */
    private val tlSpin   = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val techSpin = JSpinner(SpinnerNumberModel(0, 0, 99, 1))
    private val engSpin  = JSpinner(SpinnerNumberModel(0, 0, 99, 1))

    private val taskArea   = JTextArea(3, 60)
    private val promptArea = JTextArea(3, 60)

    private val rolePrompts = mutableMapOf<String, String>()

    /* корень */
    private val root = JPanel(BorderLayout())
    val component: JPanel = root.apply { buildMain() }

    /* ---------------- MAIN ---------------- */
    private fun buildMain() {
        root.removeAll()

        /* пустая шапка */
        root.add(Box.Filler(Dimension(1, 80), Dimension(1, 80), Dimension(Int.MAX_VALUE, 80)),
            BorderLayout.NORTH)

        /* роли по центру */
        root.add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

                add(Box.createVerticalGlue())
                add(roleRow("Team-leads", tlSpin,  "teamLead"));   add(Box.createVerticalGlue())
                add(roleRow("Tech-leads", techSpin, "techLead"));  add(Box.createVerticalGlue())
                add(roleRow("Engineers",  engSpin,  "engineer"));  add(Box.createVerticalGlue())
            },
            BorderLayout.CENTER
        )

        /* Task + Submit снизу */
        root.add(
            JPanel(BorderLayout()).apply {
                border = EmptyBorder(8, 8, 8, 8)
                add(JLabel("Task:"), BorderLayout.WEST)
                add(JScrollPane(taskArea), BorderLayout.CENTER)
                add(JButton("Submit").apply { addActionListener { switchToChat() } }, BorderLayout.EAST)
            },
            BorderLayout.SOUTH
        )

        refresh()
    }

    private fun roleRow(label: String, spinner: JSpinner, key: String) =
        JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(hyperlink(label) {
                val txt = Messages.showInputDialog(
                    project, "Prompt for $label:", "Role Prompt",
                    null, rolePrompts[key] ?: "", null
                ) ?: return@hyperlink
                rolePrompts[key] = txt.trim()
            }, BorderLayout.WEST)
            add(spinner, BorderLayout.EAST)
            maximumSize = preferredSize
        }

    /* ---------------- CHAT ---------------- */
    private lateinit var chatPane: JPanel
    private lateinit var chatScroll: JScrollPane
    private lateinit var inputField: JTextArea

    private fun switchToChat() {
        if (taskArea.text.isBlank()) {
            Messages.showErrorDialog(project, "Task cannot be empty", "Agent Team Builder")
            return
        }

        /* базовая конфигурация — один раз */
        TeamStore.get().add(
            TeamConfig(
                taskArea.text.trim(),
                tlSpin.value as Int,
                techSpin.value as Int,
                engSpin.value as Int,
                globalPrompt = "",
                rolePrompts  = rolePrompts.toMap()
            )
        )

        root.removeAll()

        /* история сообщений */
        chatPane = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 8, 8, 8)
        }
        chatScroll = JScrollPane(chatPane,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)

        root.add(chatScroll, BorderLayout.CENTER)

        /* поле ввода + Send */
        inputField = promptArea
        root.add(
            JPanel(BorderLayout()).apply {
                border = EmptyBorder(8, 8, 8, 8)
                add(JScrollPane(inputField), BorderLayout.CENTER)
                add(JButton("Send").apply { addActionListener { sendMessage() } }, BorderLayout.EAST)
            },
            BorderLayout.SOUTH
        )

        refresh()
    }

    /* ---------------- отправка одного сообщения ---------------- */
    private fun sendMessage() {
        val txt = inputField.text.trim()
        if (txt.isEmpty()) return
        inputField.text = ""

        chatPane.add(makeBubble(txt))
        chatPane.add(Box.createVerticalStrut(6))
        refresh()

        SwingUtilities.invokeLater {
            chatScroll.verticalScrollBar.value = chatScroll.verticalScrollBar.maximum
        }
    }

    /* bubble-панель для одного сообщения */
    private fun makeBubble(text: String): JPanel =
        JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            isOpaque = false

            val bubble = JLabel("<html>${text.replace("\n", "<br>")}</html>").apply {
                border = EmptyBorder(6, 8, 6, 8)
                background = Color(0xE8E8E8)
                isOpaque = true
            }
            bubble.border = RoundedBorder(Color(0xCCCCCC))
            add(bubble, BorderLayout.WEST)
        }

    /* ---------------- helpers ---------------- */
    private fun hyperlink(text: String, onClick: () -> Unit) =
        JLabel("<html><u>$text</u></html>").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent?) = onClick()
            })
        }

    private class RoundedBorder(private val color: Color) : LineBorder(color, 1, true)

    private fun refresh() { root.revalidate(); root.repaint() }
}
