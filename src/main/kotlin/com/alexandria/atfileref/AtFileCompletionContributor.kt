package com.alexandria.atfileref

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.patterns.StandardPatterns
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.codeStyle.NameUtil

private const val MAX_RESULTS = 50
private const val OPEN_FILE_BONUS = 15.0

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

        // Restart completion whenever the typed prefix changes so results are re-computed
        result.restartCompletionOnPrefixChange(StandardPatterns.string())

        val openResult = result.withPrefixMatcher(AtFilePrefixMatcher(query))

        if (query.isEmpty()) {
            fillEmptyQueryResults(openResult, fileIndex, basePath, project)
        } else if (query.contains('/')) {
            fillPathQueryResults(query, openResult, fileIndex, basePath, project)
        } else {
            fillFilenameQueryResults(query, openResult, fileIndex, basePath, project)
        }
    }

    /**
     * Empty query: show all non-excluded, non-directory files sorted by:
     * open files first, then by depth (shallow first), then alphabetically.
     */
    private fun fillEmptyQueryResults(
        result: CompletionResultSet,
        fileIndex: ProjectFileIndex,
        basePath: String,
        project: Project
    ) {
        data class FileEntry(val vf: VirtualFile, val relativePath: String, val depth: Int, val isOpen: Boolean)

        val openPaths = getOpenFilePaths(project, basePath)
        val entries = mutableListOf<FileEntry>()

        fileIndex.iterateContent { vf ->
            if (!vf.isDirectory && !fileIndex.isExcluded(vf)) {
                val relativePath = computeRelativePath(vf, basePath)
                if (relativePath != null) {
                    val depth = relativePath.count { it == '/' }
                    entries.add(FileEntry(vf, relativePath, depth, relativePath in openPaths))
                }
            }
            true
        }

        entries.sortWith(
            compareByDescending<FileEntry> { it.isOpen }
                .thenBy { it.depth }
                .thenBy { it.relativePath.lowercase() }
        )

        for ((index, entry) in entries.take(MAX_RESULTS).withIndex()) {
            val parentDir = entry.vf.parent?.let { computeRelativePath(it, basePath) } ?: ""
            val element = LookupElementBuilder.create(entry.relativePath)
                .withPresentableText(entry.vf.name)
                .withTypeText(parentDir, true)
                .withIcon(entry.vf.fileType.icon)
                .withInsertHandler(atInsertHandler(entry.relativePath))

            val priority = (MAX_RESULTS - index).toDouble()
            result.addElement(PrioritizedLookupElement.withPriority(element, priority))
        }
    }

    /**
     * Filename-based fuzzy search (query without `/`).
     */
    private fun fillFilenameQueryResults(
        query: String,
        result: CompletionResultSet,
        fileIndex: ProjectFileIndex,
        basePath: String,
        project: Project
    ) {
        val queryLower = query.lowercase()
        val fuzzyMatcher = NameUtil.buildMatcher("*$query").build()
        val projectScope = GlobalSearchScope.projectScope(project)
        val openPaths = getOpenFilePaths(project, basePath)

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
                val openBonus = if (relativePath in openPaths) OPEN_FILE_BONUS else 0.0

                scored.add(ScoredFile(vf, filename, relativePath, priority + pathBonus + openBonus))
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

    /**
     * Path-based search (query contains `/`). Matches against full relative paths.
     */
    private fun fillPathQueryResults(
        query: String,
        result: CompletionResultSet,
        fileIndex: ProjectFileIndex,
        basePath: String,
        project: Project
    ) {
        val fuzzyMatcher = NameUtil.buildMatcher("*$query").build()
        val openPaths = getOpenFilePaths(project, basePath)

        data class ScoredFile(val vf: VirtualFile, val relativePath: String, val priority: Double)

        val scored = mutableListOf<ScoredFile>()

        fileIndex.iterateContent { vf ->
            if (!vf.isDirectory && !fileIndex.isExcluded(vf)) {
                val relativePath = computeRelativePath(vf, basePath)
                if (relativePath != null && fuzzyMatcher.matches(relativePath)) {
                    val degree = fuzzyMatcher.matchingDegree(relativePath)
                    val openBonus = if (relativePath in openPaths) OPEN_FILE_BONUS else 0.0
                    scored.add(ScoredFile(vf, relativePath, 40.0 + degree.coerceIn(-20, 20) + openBonus))
                }
            }
            true
        }

        scored.sortByDescending { it.priority }

        for (sf in scored.take(MAX_RESULTS)) {
            val element = LookupElementBuilder.create(sf.relativePath)
                .withPresentableText(sf.relativePath)
                .withIcon(sf.vf.fileType.icon)
                .withInsertHandler(atInsertHandler(sf.relativePath))

            result.addElement(PrioritizedLookupElement.withPriority(element, sf.priority))
        }
    }

    private fun getOpenFilePaths(project: Project, basePath: String): Set<String> {
        return FileEditorManager.getInstance(project).openFiles
            .mapNotNull { computeRelativePath(it, basePath) }
            .toSet()
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

    private class AtFilePrefixMatcher(prefix: String) : PrefixMatcher(prefix) {
        private val fuzzyMatcher by lazy {
            if (prefix.isNotEmpty()) NameUtil.buildMatcher("*$prefix").build() else null
        }

        override fun prefixMatches(name: String): Boolean {
            if (prefix.isEmpty()) return true
            val queryLower = prefix.lowercase()
            val filename = name.substringAfterLast('/').lowercase()
            if (filename.contains(queryLower)) return true
            if (name.lowercase().contains(queryLower)) return true
            return fuzzyMatcher?.let { it.matches(filename) || it.matches(name) } ?: false
        }

        override fun cloneWithPrefix(prefix: String) = AtFilePrefixMatcher(prefix)
    }
}
