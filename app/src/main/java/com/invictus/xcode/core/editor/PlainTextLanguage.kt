package com.invictus.xcode.core.editor

import android.os.Bundle
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference

/**
 * Language for files with no TextMate grammar (unknown extension, or a scope not bundled).
 * [EmptyLanguage] otherwise leaves such files with no autocomplete at all; this adds the same
 * word/keyword-only completion TextMateLanguage gets via [setCompleterKeywords] -- just without
 * syntax highlighting or an analyzer to source in-file identifiers from, so only [keywords]
 * (usually empty here, since an unknown file has no known language) actually produce matches.
 */
class PlainTextLanguage(keywords: List<String> = emptyList()) : EmptyLanguage() {

    private val autoComplete = IdentifierAutoComplete().apply {
        setKeywords(keywords.toTypedArray(), false)
    }

    fun setCompleterKeywords(keywords: List<String>) {
        autoComplete.setKeywords(keywords.toTypedArray(), false)
    }

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        val prefix = CompletionHelper.computePrefix(content, position) { Character.isJavaIdentifierPart(it) }
        autoComplete.requireAutoComplete(content, position, prefix, publisher, null)
    }
}
