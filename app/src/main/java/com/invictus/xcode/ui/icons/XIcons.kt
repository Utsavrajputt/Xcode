package com.invictus.xcode.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
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

    // M5 preview feature: not in the Material Symbols cmp artifact's current release,
    // so drawn from path data the same way, rather than risk an unverifiable binding name.
    /** M5 preview toggle, "Editor" state / code-file preview affordance. */
    val Code: ImageVector by lazy {
        icon(
            "Code",
            "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4z" +
                "M14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z",
        )
    }

    /** M5 preview toggle, "Preview" state. */
    val Visibility: ImageVector by lazy {
        icon(
            "Visibility",
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39" +
                " -6,-7.5 -11,-7.5z M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z" +
                "M12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        )
    }

    /** M5 preview toggle, "Split" state (editor top, preview bottom). */
    val HorizontalSplit: ImageVector by lazy {
        icon("HorizontalSplit", "M3,19h18v-6H3v6z M3,11h18V9H3v2z M3,5v2h18V5H3z")
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
            .build()
}
