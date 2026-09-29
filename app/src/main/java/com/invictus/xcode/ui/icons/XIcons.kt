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

    // Editor tools menu glyphs (Material paths, same hand-drawn pattern as below).
    /** Top-bar tools menu (Material "tune" — sliders), replaces the old bolt. */
    val Tune: ImageVector by lazy {
        icon(
            "Tune",
            "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10z" +
                "M15,9h2V7h4V5h-4V3h-2v6z",
        )
    }
    val ContentCopy: ImageVector by lazy {
        icon(
            "ContentCopy",
            "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7" +
                "C21,5.9 20.1,5 19,5zM19,21H8V7h11V21z",
        )
    }
    val Delete: ImageVector by lazy {
        icon("Delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    }
    val ArrowUpward: ImageVector by lazy {
        icon("ArrowUpward", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z")
    }
    val ArrowDownward: ImageVector by lazy {
        icon("ArrowDownward", "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z")
    }
    val Comment: ImageVector by lazy {
        icon("Comment", "M21.99,4c0,-1.1 -0.89,-2 -1.99,-2H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h14l4,4 -0.01,-18z")
    }

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

    /** "Show hidden files" toggle, off state (Material Symbols "visibility_off"). */
    val VisibilityOff: ImageVector by lazy {
        icon(
            "VisibilityOff",
            "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.44,-4.75" +
                " -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
                "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3" +
                " 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27z" +
                "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03" +
                " 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53" +
                " 0.53,-2.2z M11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z",
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
    /** git rebase entry point (Material "call_merge" — a branch folding back into the trunk). */
    val Rebase: ImageVector by lazy {
        icon(
            "Rebase",
            "M17,20.41L18.41,19 15,15.59 13.59,17 17,20.41zM7.5,8H11v5.59L5.59,19 7,20.41l6,-6V8h3.5L12,3.5 7.5,8z",
        )
    }
    /** git merge (Material Symbols "merge"): two lines joining into one. */
    val Merge: ImageVector by lazy {
        icon(
            "Merge",
            "M6.41,21L5,19.59l4.83,-4.83c0.75,-0.75 1.17,-1.77 1.17,-2.83V8.83L9.41,10.41L8,9l4,-4 4,4 -1.42,1.41L13,8.83v3.09c0,1.06 0.42,2.08 1.17,2.83L19,19.59 17.59,21 12,15.41 6.41,21z",
        )
    }
    val Archive: ImageVector by lazy {
        icon(
            "Archive",
            "M20.54,5.23l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6c-0.47,0 -0.88,0.21 -1.15,0.55L3.46,5.23C3.17,5.57 3,6.02 3,6.5V19c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6.5c0,-0.48 -0.17,-0.93 -0.46,-1.27zM12,17.5L6.5,12H10v-2h4v2h3.5L12,17.5zM5.12,5l0.81,-1h12l0.94,1H5.12z",
        )
    }
    val Label: ImageVector by lazy {
        icon("Label", "M17.63,5.84C17.27,5.33 16.67,5 16,5L5,5.01C3.9,5.01 3,5.9 3,7v10c0,1.1 0.9,1.99 2,1.99L16,19c0.67,0 1.27,-0.33 1.63,-0.84L22,12l-4.37,-6.16z")
    }
    val Cloud: ImageVector by lazy {
        icon("Cloud", "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z")
    }
    val Remove: ImageVector by lazy { icon("Remove", "M19,13H5v-2h14v2z") }
    val ClearAll: ImageVector by lazy { icon("ClearAll", "M5,13h14v-2H5v2zM3,17h14v-2H3v2zM7,7v2h14V7H7z") }
    val Tab: ImageVector by lazy {
        icon("Tab", "M21,3H3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM21,19H3V5h10v4h8v10z")
    }
    val AccountTree: ImageVector by lazy {
        icon(
            "AccountTree",
            "M22,11V3h-7v3H9V3H2v8h7V7h2v10h4v3h7v-8h-7v3h-2V7h2v4h7z",
        )
    }

    /** Collapse-all entry point (Material Symbols "unfold_less" — chevrons closing together). */
    val UnfoldLess: ImageVector by lazy {
        icon(
            "UnfoldLess",
            "M7.41,18.59L8.83,20 12,16.83 15.17,20l1.41,-1.41L12,14z" +
                "M16.59,5.41L15.17,4 12,7.17 8.83,4 7.41,5.41 12,10z",
        )
    }

    /** git reset entry point (Material Symbols "restore" — clock with a back arrow). */
    val Restore: ImageVector by lazy {
        icon(
            "Restore",
            "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7" +
                " -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0" +
                " 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8z",
        )
    }

    // Onboarding (startup permission stepper): storage/notification steps.
    /** Notification step (Material Symbols "notifications" — a bell). */
    val Notifications: ImageVector by lazy {
        icon(
            "Notifications",
            "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2z" +
                "M18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68" +
                "C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z",
        )
    }


    /** Info step marker (Material Symbols "info"). */
    val Info: ImageVector by lazy {
        icon(
            "Info",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "M13,17h-2v-6h2v6zM13,9h-2V7h2v2z",
        )
    }

    /** Forward step navigation (Material Symbols "arrow_forward"). */
    val ArrowForward: ImageVector by lazy {
        icon(
            "ArrowForward",
            "M12,4l-1.41,1.41L16.17,11H4v2h12.17l-5.58,5.59L12,20l8,-8z",
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
