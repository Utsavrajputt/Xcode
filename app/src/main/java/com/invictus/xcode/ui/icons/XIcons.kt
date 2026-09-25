package com.invictus.xcode.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.Add
import com.composables.icons.materialsymbols.roundedfilled.Arrow_back
import com.composables.icons.materialsymbols.roundedfilled.Bolt
import com.composables.icons.materialsymbols.roundedfilled.Check
import com.composables.icons.materialsymbols.roundedfilled.Chevron_left
import com.composables.icons.materialsymbols.roundedfilled.Chevron_right
import com.composables.icons.materialsymbols.roundedfilled.Close
import com.composables.icons.materialsymbols.roundedfilled.Edit
import com.composables.icons.materialsymbols.roundedfilled.Folder
import com.composables.icons.materialsymbols.roundedfilled.Folder_open
import com.composables.icons.materialsymbols.roundedfilled.Keyboard_arrow_down
import com.composables.icons.materialsymbols.roundedfilled.Keyboard_arrow_up
import com.composables.icons.materialsymbols.roundedfilled.More_vert
import com.composables.icons.materialsymbols.roundedfilled.Palette
import com.composables.icons.materialsymbols.roundedfilled.Push_pin
import com.composables.icons.materialsymbols.roundedfilled.Redo
import com.composables.icons.materialsymbols.roundedfilled.Refresh
import com.composables.icons.materialsymbols.roundedfilled.Save
import com.composables.icons.materialsymbols.roundedfilled.Search
import com.composables.icons.materialsymbols.roundedfilled.Settings
import com.composables.icons.materialsymbols.roundedfilled.Undo

/**
 * App icon set — the same Material Symbols Rounded glyphs xmd uses
 * (com.composables:icons-material-symbols-*-cmp). Member names are unchanged,
 * so every existing call site keeps working while rendering the nicer icons.
 */
object XIcons {
    val Folder: ImageVector = MaterialSymbols.RoundedFilled.Folder
    val FolderOpen: ImageVector = MaterialSymbols.RoundedFilled.Folder_open
    val ChevronRight: ImageVector = MaterialSymbols.RoundedFilled.Chevron_right
    val ChevronLeft: ImageVector = MaterialSymbols.RoundedFilled.Chevron_left
    val MoreVert: ImageVector = MaterialSymbols.RoundedFilled.More_vert
    val Refresh: ImageVector = MaterialSymbols.RoundedFilled.Refresh
    val Pin: ImageVector = MaterialSymbols.RoundedFilled.Push_pin
    val Search: ImageVector = MaterialSymbols.RoundedFilled.Search
    val Close: ImageVector = MaterialSymbols.RoundedFilled.Close
    val ArrowBack: ImageVector = MaterialSymbols.RoundedFilled.Arrow_back
    val Undo: ImageVector = MaterialSymbols.RoundedFilled.Undo
    val Redo: ImageVector = MaterialSymbols.RoundedFilled.Redo
    val Save: ImageVector = MaterialSymbols.RoundedFilled.Save
    val Palette: ImageVector = MaterialSymbols.RoundedFilled.Palette
    val Check: ImageVector = MaterialSymbols.RoundedFilled.Check
    val KeyboardArrowUp: ImageVector = MaterialSymbols.RoundedFilled.Keyboard_arrow_up
    val KeyboardArrowDown: ImageVector = MaterialSymbols.RoundedFilled.Keyboard_arrow_down
    val Edit: ImageVector = MaterialSymbols.RoundedFilled.Edit
    val Bolt: ImageVector = MaterialSymbols.RoundedFilled.Bolt
    val Settings: ImageVector = MaterialSymbols.RoundedFilled.Settings

    // Convenience alias used by some newer call sites.
    val Add: ImageVector = MaterialSymbols.RoundedFilled.Add
}
