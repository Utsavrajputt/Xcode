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

    // M6 git feature: these glyphs aren't in the cmp artifact's current release, so
    // they use the same hand-drawn path pattern as the icons above.
    /** Source control entry point (Material Symbols "commit"). */
    val Commit: ImageVector by lazy {
        icon(
            "Commit",
            "M21,12c0,-4.97 -4.03,-9 -9,-9s-9,4.03 -9,9c0,4.63 3.5,8.44 8,8.94v-2.02c-3.59,-0.45" +
                " -6.38,-3.46 -6.38,-7.2 0,-3.98 3.22,-7.2 7.2,-7.2s7.2,3.22 7.2,7.2c0,1.95 -0.78,3.72" +
                " -2.05,5.01l1.41,1.41C20.16,17.44 21,14.91 21,12zM12,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2" +
                " 2,0.9 2,2 -0.9,2 -2,2z",
        )
    }
    val CloudUpload: ImageVector by lazy {
        icon(
            "CloudUpload",
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14" +
                "c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM10,17l-3.5,-3.5" +
                " 1.41,-1.41L10,14.17 15.18,9l1.41,1.41L10,17z",
        )
    }
    val CloudDownload: ImageVector by lazy {
        icon(
            "CloudDownload",
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14" +
                "c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM17,13l-5,5 -5,-5h3V9h4v4h3z",
        )
    }
    val Sync: ImageVector by lazy {
        icon(
            "Sync",
            "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57" +
                " 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74" +
                "C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z",
        )
    }
    val Key: ImageVector by lazy {
        icon(
            "Key",
            "M12.65,10C11.83,7.67 9.61,6 7,6c-3.31,0 -6,2.69 -6,6s2.69,6 6,6c2.61,0 4.83,-1.67 5.65,-4H17v4h4" +
                "v-4h2v-4H12.65zM7,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z",
        )
    }
    val Person: ImageVector by lazy {
        icon(
            "Person",
            "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2" +
                "c0,-2.66 -5.33,-4 -8,-4z",
        )
    }
    val AccountTree: ImageVector by lazy {
        icon(
            "AccountTree",
            "M22,11V3h-7v3H9V3H2v8h7V7h2v10h4v3h7v-8h-7v3h-2V7h2v4h7z",
        )
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
