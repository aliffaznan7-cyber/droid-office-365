package com.example.core.shortcuts

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

enum class ShortcutCommand {
    SAVE,
    EXPORT_FLATTEN,
    OPEN_FILE,
    NEW_DOCX,
    CLOSE_TAB,
    NEXT_TAB,
    FIND,
    FIND_REPLACE,
    PRINT,
    UNDO,
    REDO,
    ZOOM_IN,
    ZOOM_OUT,
    ZOOM_RESET,
    TOGGLE_RIBBON,
    FORMAT_BOLD,
    FORMAT_ITALIC,
    FORMAT_UNDERLINE,
    INSERT_PAGE_BREAK,
    TOOL_BROWSE,
    TOOL_SELECT,
    TOOL_HIGHLIGHT,
    TOOL_INK,
    ESCAPE,
    SHOW_SHORTCUTS_HELP
}

/**
 * Physical keyboard shortcut dispatcher & Android KeyboardShortcutsHelper (Meta+/) provider
 * optimized for Xiaomi Pad 7 keyboard attachments.
 */
object KeyboardShortcutRegistry {

    fun matchCommand(event: ComposeKeyEvent): ShortcutCommand? {
        if (event.type != KeyEventType.KeyDown) return null
        val ctrl = event.isCtrlPressed
        val shift = event.isShiftPressed

        return when {
            ctrl && shift && event.key == Key.S -> ShortcutCommand.EXPORT_FLATTEN
            ctrl && event.key == Key.S -> ShortcutCommand.SAVE
            ctrl && event.key == Key.P -> ShortcutCommand.PRINT
            ctrl && event.key == Key.O -> ShortcutCommand.OPEN_FILE
            ctrl && event.key == Key.N -> ShortcutCommand.NEW_DOCX
            ctrl && event.key == Key.W -> ShortcutCommand.CLOSE_TAB
            ctrl && event.key == Key.Tab -> ShortcutCommand.NEXT_TAB
            ctrl && event.key == Key.F -> ShortcutCommand.FIND
            ctrl && event.key == Key.H -> ShortcutCommand.FIND_REPLACE
            ctrl && shift && event.key == Key.Z -> ShortcutCommand.REDO
            ctrl && event.key == Key.Z -> ShortcutCommand.UNDO
            ctrl && event.key == Key.Y -> ShortcutCommand.REDO
            ctrl && event.key == Key.B -> ShortcutCommand.FORMAT_BOLD
            ctrl && event.key == Key.I -> ShortcutCommand.FORMAT_ITALIC
            ctrl && event.key == Key.U -> ShortcutCommand.FORMAT_UNDERLINE
            ctrl && event.key == Key.Enter -> ShortcutCommand.INSERT_PAGE_BREAK
            ctrl && (event.key == Key.Equals || event.key == Key.NumPadAdd) -> ShortcutCommand.ZOOM_IN
            ctrl && (event.key == Key.Minus || event.key == Key.NumPadSubtract) -> ShortcutCommand.ZOOM_OUT
            ctrl && event.key == Key.Zero -> ShortcutCommand.ZOOM_RESET
            ctrl && event.key == Key.F1 -> ShortcutCommand.TOGGLE_RIBBON
            ctrl && event.key == Key.One -> ShortcutCommand.TOOL_BROWSE
            ctrl && event.key == Key.Two -> ShortcutCommand.TOOL_SELECT
            ctrl && event.key == Key.Three -> ShortcutCommand.TOOL_HIGHLIGHT
            ctrl && event.key == Key.Four -> ShortcutCommand.TOOL_INK
            event.key == Key.Escape -> ShortcutCommand.ESCAPE
            event.key == Key.F1 -> ShortcutCommand.SHOW_SHORTCUTS_HELP
            else -> null
        }
    }

    fun buildShortcutGroups(): List<KeyboardShortcutGroup> {
        val workspaceGroup = KeyboardShortcutGroup(
            "Workspace & Tabs",
            listOf(
                KeyboardShortcutInfo("Save document", KeyEvent.KEYCODE_S, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo(
                    "Flatten & Export PDF",
                    KeyEvent.KEYCODE_S,
                    KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
                ),
                KeyboardShortcutInfo("Print document", KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Open file", KeyEvent.KEYCODE_O, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("New Word document", KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Close active tab", KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Next document tab", KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Find in document", KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Find & Replace", KeyEvent.KEYCODE_H, KeyEvent.META_CTRL_ON)
            )
        )

        val wordGroup = KeyboardShortcutGroup(
            "Word Processor Formatting",
            listOf(
                KeyboardShortcutInfo("Bold", KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Italic", KeyEvent.KEYCODE_I, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Underline", KeyEvent.KEYCODE_U, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Insert Page Break", KeyEvent.KEYCODE_ENTER, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Undo", KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Redo", KeyEvent.KEYCODE_Y, KeyEvent.META_CTRL_ON)
            )
        )

        val pdfEditingGroup = KeyboardShortcutGroup(
            "PDF Reader, Zoom & Stylus Tools",
            listOf(
                KeyboardShortcutInfo("Zoom in", KeyEvent.KEYCODE_EQUALS, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Zoom out", KeyEvent.KEYCODE_MINUS, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Reset zoom (100%)", KeyEvent.KEYCODE_0, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Browse / Pan tool", KeyEvent.KEYCODE_1, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Text selection tool", KeyEvent.KEYCODE_2, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Highlight tool", KeyEvent.KEYCODE_3, KeyEvent.META_CTRL_ON),
                KeyboardShortcutInfo("Stylus Ink pen", KeyEvent.KEYCODE_4, KeyEvent.META_CTRL_ON)
            )
        )
        return listOf(workspaceGroup, wordGroup, pdfEditingGroup)
    }
}
