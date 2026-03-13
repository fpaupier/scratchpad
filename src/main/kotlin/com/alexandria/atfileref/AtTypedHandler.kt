package com.alexandria.atfileref

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class AtTypedHandler : TypedHandlerDelegate() {

    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile
    ): Result {
        val virtualFile = file.virtualFile ?: return Result.CONTINUE
        if (!isSupportedFile(virtualFile)) return Result.CONTINUE

        if (charTyped == '@') {
            // Fresh @ typed — always trigger popup
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            return Result.STOP
        }

        // Continuation chars: re-trigger popup if there's an active @query context
        if (charTyped.isLetterOrDigit() || charTyped in ".-_/") {
            // checkAutoPopup fires before the char is inserted, so the document
            // still reflects state before this keystroke. findAtQuery checks the
            // line up to the current caret — if it finds an @context, the user
            // is continuing to type a file reference.
            val caretOffset = editor.caretModel.offset
            if (findAtQuery(editor.document, caretOffset) != null) {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                return Result.STOP
            }
        }

        return Result.CONTINUE
    }
}
