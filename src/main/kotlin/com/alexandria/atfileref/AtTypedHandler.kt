package com.alexandria.atfileref

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.ide.scratch.ScratchUtil
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
        if (charTyped == '@') {
            val virtualFile = file.virtualFile ?: return Result.CONTINUE
            if (ScratchUtil.isScratch(virtualFile)) {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                return Result.STOP
            }
        }
        return Result.CONTINUE
    }
}
