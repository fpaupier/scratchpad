package com.alexandria.atfileref

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
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
        if (!isSupportedFile(virtualFile)) return

        val project = parameters.editor.project ?: return
        val document = parameters.editor.document
        val caretOffset = parameters.offset

        val query = findAtQuery(document, caretOffset) ?: return

        val basePath = project.basePath ?: return
        val fileIndex = ProjectFileIndex.getInstance(project)

        // Use ALWAYS_TRUE so IntelliJ doesn't filter our results — we handle matching ourselves
        val openResult = result.withPrefixMatcher(PrefixMatcher.ALWAYS_TRUE)

        if (query.isEmpty()) {
            fillEmptyQueryResults(openResult, fileIndex, basePath)
        } else {
            fillQueryResults(query, openResult, fileIndex, basePath, project)
        }
    }

    /**
     * Empty query: show all non-excluded, non-directory files sorted by depth (shallow first)
     * then alphabetically, capped at MAX_RESULTS.
     */
    private fun fillEmptyQueryResults(
        result: CompletionResultSet,
        fileIndex: ProjectFileIndex,
        basePath: String
    ) {
        data class FileEntry(val vf: VirtualFile, val relativePath: String, val depth: Int)

        val entries = mutableListOf<FileEntry>()

        fileIndex.iterateContent { vf ->
            if (!vf.isDirectory && !fileIndex.isExcluded(vf)) {
                val relativePath = computeRelativePath(vf, basePath)
                if (relativePath != null) {
                    val depth = relativePath.count { it == '/' }
                    entries.add(FileEntry(vf, relativePath, depth))
                }
            }
            true // continue iterating
        }

        // Sort by depth (shallow first), then alphabetically
        entries.sortWith(compareBy<FileEntry> { it.depth }.thenBy { it.relativePath.lowercase() })

        for (entry in entries.take(MAX_RESULTS)) {
            val parentDir = entry.vf.parent?.let { computeRelativePath(it, basePath) } ?: ""
            val element = LookupElementBuilder.create(entry.relativePath)
                .withPresentableText(entry.vf.name)
                .withTypeText(parentDir, true)
                .withIcon(entry.vf.fileType.icon)
                .withInsertHandler(atInsertHandler(entry.relativePath))

            // Use index-based priority to preserve sort order (higher = shown first)
            val priority = (MAX_RESULTS - entries.indexOf(entry)).toDouble()
            result.addElement(PrioritizedLookupElement.withPriority(element, priority))
        }
    }

    /**
     * Query present: fuzzy-search project files using MinusculeMatcher.
     */
    private fun fillQueryResults(
        query: String,
        result: CompletionResultSet,
        fileIndex: ProjectFileIndex,
        basePath: String,
        project: com.intellij.openapi.project.Project
    ) {
        val queryLower = query.lowercase()
        val fuzzyMatcher = NameUtil.buildMatcher("*$query").build()
        val projectScope = GlobalSearchScope.projectScope(project)

        data class ScoredFile(val vf: VirtualFile, val filename: String, val relativePath: String, val priority: Double)

        val scored = mutableListOf<ScoredFile>()

        for (filename in FilenameIndex.getAllFilenames(project)) {
            val filenameLower = filename.lowercase()

            val priority: Double = when {
                filenameLower == queryLower -> 100.0
                filenameLower.startsWith(queryLower) -> 80.0
                filenameLower.contains(queryLower) -> 60.0
                fuzzyMatcher.matches(filename) -> {
                    val degree = fuzzyMatcher.matchingDegree(filename)
                    40.0 + degree.coerceIn(-20, 20)
                }
                else -> continue
            }

            val files = FilenameIndex.getVirtualFilesByName(filename, projectScope)
            for (vf in files) {
                if (!fileIndex.isInContent(vf)) continue
                if (fileIndex.isExcluded(vf)) continue
                val relativePath = computeRelativePath(vf, basePath) ?: continue

                val pathBonus = if (relativePath.lowercase().contains(queryLower) &&
                    !filenameLower.contains(queryLower)) 10.0 else 0.0

                scored.add(ScoredFile(vf, filename, relativePath, priority + pathBonus))
            }
        }

        scored.sortByDescending { it.priority }

        for (sf in scored.take(MAX_RESULTS)) {
            val parentDir = sf.vf.parent?.let { computeRelativePath(it, basePath) } ?: ""

            val element = LookupElementBuilder.create(sf.relativePath)
                .withPresentableText(sf.filename)
                .withTypeText(parentDir, true)
                .withIcon(sf.vf.fileType.icon)
                .withInsertHandler(atInsertHandler(sf.relativePath))

            result.addElement(PrioritizedLookupElement.withPriority(element, sf.priority))
        }
    }

    private fun atInsertHandler(relativePath: String): InsertHandler<LookupElement> {
        return InsertHandler { context, _ ->
            val startOffset = context.startOffset
            val doc = context.document
            val lineNum = doc.getLineNumber(startOffset)
            val lineStartOff = doc.getLineStartOffset(lineNum)
            val textBeforeInsert = doc.getText(TextRange(lineStartOff, startOffset))
            val atPos = textBeforeInsert.lastIndexOf('@')
            if (atPos >= 0) {
                val atOffset = lineStartOff + atPos
                doc.replaceString(atOffset + 1, context.tailOffset, relativePath)
            }
        }
    }

    private fun computeRelativePath(vf: VirtualFile, basePath: String): String? {
        val path = vf.path
        if (!path.startsWith(basePath)) return null
        val relative = path.removePrefix(basePath).removePrefix("/")
        return relative.ifEmpty { null }
    }
}
