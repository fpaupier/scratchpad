package com.alexandria.atfileref

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.codeStyle.NameUtil

private const val MAX_RESULTS = 50

class AtFileCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        val virtualFile = file.virtualFile ?: return
        if (!ScratchUtil.isScratch(virtualFile)) return

        val project = parameters.editor.project ?: return
        val document = parameters.editor.document
        val caretOffset = parameters.offset
        val lineNumber = document.getLineNumber(caretOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineTextBeforeCaret = document.getText(TextRange(lineStart, caretOffset))

        val atIndex = lineTextBeforeCaret.lastIndexOf('@')
        if (atIndex < 0) return

        val query = lineTextBeforeCaret.substring(atIndex + 1)
        if (query.isEmpty()) return

        val queryLower = query.lowercase()

        // Use ALWAYS_TRUE so IntelliJ doesn't filter our results — we handle matching ourselves
        val openResult = result.withPrefixMatcher(PrefixMatcher.ALWAYS_TRUE)

        // MinusculeMatcher for fuzzy/camel-hump matching (same engine as Search Everywhere)
        val fuzzyMatcher = NameUtil.buildMatcher("*$query").build()

        val basePath = project.basePath ?: return
        val projectScope = GlobalSearchScope.projectScope(project)
        val fileIndex = ProjectFileIndex.getInstance(project)

        data class ScoredFile(val vf: VirtualFile, val filename: String, val relativePath: String, val priority: Double)

        val scored = mutableListOf<ScoredFile>()

        for (filename in FilenameIndex.getAllFilenames(project)) {
            val filenameLower = filename.lowercase()

            // Match by: exact, prefix, substring (case-insensitive), or fuzzy camel-hump
            val priority: Double = when {
                filenameLower == queryLower -> 100.0
                filenameLower.startsWith(queryLower) -> 80.0
                filenameLower.contains(queryLower) -> 60.0
                fuzzyMatcher.matches(filename) -> {
                    // Use the matcher's degree as a sub-score for fuzzy matches
                    val degree = fuzzyMatcher.matchingDegree(filename)
                    40.0 + degree.coerceIn(-20, 20)
                }
                else -> continue
            }

            val files = FilenameIndex.getVirtualFilesByName(filename, projectScope)
            for (vf in files) {
                if (!fileIndex.isInContent(vf)) continue
                val relativePath = computeRelativePath(vf, basePath) ?: continue

                // Boost files whose path also contains the query
                val pathBonus = if (relativePath.lowercase().contains(queryLower) &&
                    !filenameLower.contains(queryLower)) 10.0 else 0.0

                scored.add(ScoredFile(vf, filename, relativePath, priority + pathBonus))
            }
        }

        // Sort by priority descending, take top N
        scored.sortByDescending { it.priority }

        for ((i, sf) in scored.take(MAX_RESULTS).withIndex()) {
            val parentDir = sf.vf.parent?.let { computeRelativePath(it, basePath) } ?: ""

            val element = LookupElementBuilder.create(sf.relativePath)
                .withPresentableText(sf.filename)
                .withTypeText(parentDir, true)
                .withIcon(sf.vf.fileType.icon)
                .withInsertHandler { context, _ ->
                    val startOffset = context.startOffset
                    val doc = context.document
                    val lineNum = doc.getLineNumber(startOffset)
                    val lineStartOff = doc.getLineStartOffset(lineNum)
                    val textBeforeInsert = doc.getText(
                        TextRange(lineStartOff, startOffset)
                    )
                    val atPos = textBeforeInsert.lastIndexOf('@')
                    if (atPos >= 0) {
                        val atOffset = lineStartOff + atPos
                        doc.replaceString(atOffset + 1, context.tailOffset, sf.relativePath)
                    }
                }

            // Use priority so IntelliJ preserves our sort order
            val prioritized = PrioritizedLookupElement.withPriority(element, sf.priority)
            openResult.addElement(prioritized)
        }
    }

    private fun computeRelativePath(vf: VirtualFile, basePath: String): String? {
        val path = vf.path
        if (!path.startsWith(basePath)) return null
        val relative = path.removePrefix(basePath).removePrefix("/")
        return relative.ifEmpty { null }
    }
}
