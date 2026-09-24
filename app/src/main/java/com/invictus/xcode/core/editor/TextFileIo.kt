package com.invictus.xcode.core.editor

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

enum class LineEnding(val sequence: String) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r"),
}

/** A decoded text file. [text] always uses `\n`; [lineEnding] says what to write back. */
class LoadedText(
    val text: String,
    val charset: Charset,
    val hasBom: Boolean,
    val lineEnding: LineEnding,
)

class FileTooLargeException(val sizeBytes: Long) : IOException("File too large: $sizeBytes bytes")

/**
 * Reads and writes whole text files, remembering charset, BOM and line endings so a save
 * doesn't silently rewrite them (a CRLF file stays CRLF, a BOM stays a BOM).
 * Blocking: call from Dispatchers.IO.
 */
object TextFileIo {

    /** Anything bigger waits for paged loading (M4). */
    const val MAX_BYTES: Long = 32L * 1024 * 1024

    private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val BOM_UTF16_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val BOM_UTF16_BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    @Throws(IOException::class)
    fun read(file: File): LoadedText {
        val size = file.length()
        if (size > MAX_BYTES) throw FileTooLargeException(size)
        val bytes = file.readBytes()

        var charset: Charset = Charsets.UTF_8
        var offset = 0
        when {
            bytes.startsWith(BOM_UTF8) -> offset = BOM_UTF8.size
            bytes.startsWith(BOM_UTF16_LE) -> {
                charset = Charsets.UTF_16LE
                offset = BOM_UTF16_LE.size
            }
            bytes.startsWith(BOM_UTF16_BE) -> {
                charset = Charsets.UTF_16BE
                offset = BOM_UTF16_BE.size
            }
        }
        val hasBom = offset > 0

        val decoded = try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                .toString()
        } catch (_: CharacterCodingException) {
            // Not valid in the detected charset: fall back to one that can't fail so the
            // file at least opens (and is written back the same way).
            charset = Charsets.ISO_8859_1
            String(bytes, offset, bytes.size - offset, charset)
        }

        val ending = detectLineEnding(decoded)
        val normalized = when (ending) {
            LineEnding.LF -> decoded
            LineEnding.CRLF -> decoded.replace("\r\n", "\n")
            LineEnding.CR -> decoded.replace("\r", "\n")
        }
        return LoadedText(normalized, charset, hasBom, ending)
    }

    @Throws(IOException::class)
    fun write(file: File, text: String, charset: Charset, hasBom: Boolean, lineEnding: LineEnding) {
        val out = when (lineEnding) {
            LineEnding.LF -> text
            LineEnding.CRLF -> text.replace("\n", "\r\n")
            LineEnding.CR -> text.replace("\n", "\r")
        }
        // Written in place (not temp file + rename) so file mode bits, e.g. a script's
        // executable flag, survive the save.
        FileOutputStream(file).use { stream ->
            if (hasBom) {
                when (charset) {
                    Charsets.UTF_16LE -> stream.write(BOM_UTF16_LE)
                    Charsets.UTF_16BE -> stream.write(BOM_UTF16_BE)
                    else -> stream.write(BOM_UTF8)
                }
            }
            stream.write(out.toByteArray(charset))
        }
    }

    private fun detectLineEnding(text: String): LineEnding {
        var crlf = 0
        var lf = 0
        var cr = 0
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '\r' -> if (i + 1 < text.length && text[i + 1] == '\n') {
                    crlf++
                    i++
                } else {
                    cr++
                }
                '\n' -> lf++
            }
            i++
        }
        return when {
            crlf > 0 && crlf >= lf && crlf >= cr -> LineEnding.CRLF
            lf > 0 && lf >= cr -> LineEnding.LF
            cr > 0 -> LineEnding.CR
            else -> LineEnding.LF
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
