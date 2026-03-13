package com.alexandria.atfileref

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

private val SUPPORTED_EXTENSIONS = setOf("txt", "md")

/**
 * Returns true for scratch files, .txt, and .md files.
 */
fun isSupportedFile(vf: VirtualFile): Boolean {
    if (ScratchUtil.isScratch(vf)) return true
    val ext = vf.extension?.lowercase() ?: return false
    return ext in SUPPORTED_EXTENSIONS
}

/**
 * Finds the @query context on the current line at [caretOffset].
 *
 * Returns:
 * - `null` if there is no valid @ context (no @, or @ is preceded by a non-whitespace char like in emails)
 * - `""` (empty string) if @ was just typed with nothing after it
 * - the query string after @
 */
fun findAtQuery(document: Document, caretOffset: Int): String? {
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStart = document.getLineStartOffset(lineNumber)
    val lineText = document.getText(TextRange(lineStart, caretOffset))

    val atIndex = lineText.lastIndexOf('@')
    if (atIndex < 0) return null

    // Whitespace-before-@ heuristic: @ must be at line start or preceded by whitespace
    if (atIndex > 0 && !lineText[atIndex - 1].isWhitespace()) return null

    return lineText.substring(atIndex + 1)
}
