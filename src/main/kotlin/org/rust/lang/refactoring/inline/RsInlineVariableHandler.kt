/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.inline

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.runWriteCommandAction

class RsInlineVariableHandler : InlineActionHandler() {
    override fun inlineElement(project: Project, editor: Editor, element: PsiElement) {
        println("RsInlineVariableHandler.inlineElement")
        println(element.textOffset)
        if (element !is RsElement) return;
        println("is RsElement")

        val decl = element.ancestorStrict<RsLetDecl>() ?: return
        val name = decl.pat?.descendantOfTypeStrict<RsPatBinding>()?.identifier?.text ?: return
        var reference = TargetElementUtil.findReference(editor, editor.caretModel.offset)
        if (reference != null && decl.pat!!.isAncestorOf(reference.element)) {
            reference = null
        }

        if (decl.descendantOfTypeStrict<RsPatTup>() != null) {
            return showErrorHint(project, editor,
                "Cannot inline variable '$name' with tuple-unpacking assignment")
        }

        val references = ReferencesSearch.search(element).findAll()
        val initializer = extractInitializer(project, editor, decl, references) ?: return

        if (references.count() == 0) {
            return showErrorHint(project, editor, "Variable '$name' is never used")
        }

        val refsInOriginalFile = references
            .filter { it.element.containingFile == decl.containingFile }
            .map { it.element }
        highlightElements(project, editor, refsInOriginalFile)

        val dialog = RsInlineVariableDialog(decl, reference, initializer)
        if (/*withPrompt && */!ApplicationManager.getApplication().isUnitTestMode && dialog.shouldBeShown()) {
            dialog.show()
            if (!dialog.isOK /*&& hasHighlightings*/) {
                val statusBar = WindowManager.getInstance().getStatusBar(decl.project)
                statusBar?.info = RefactoringBundle.message("press.escape.to.remove.the.highlighting")
            }
        } else {
            dialog.doAction()
        }
    }

    private fun extractInitializer(
        project: Project,
        editor: Editor,
        decl: RsLetDecl,
        refs: Collection<PsiReference>
    ): RsExpr? {
        val declInitializer = decl.expr
        val name = decl.pat?.descendantOfTypeStrict<RsNamedElement>()?.name ?: "<unnamed>"
        val writeUsages = refs
            .filter {
                val expr = it.element.ancestorOrSelf<RsBinaryExpr>()
                // maybe the strict ancestor shouldn't be here, if we forbid tuples on lhs
                expr?.left?.isAncestorOf(it.element) == true && expr.isAssignBinaryExpr
            }.map { it.element.ancestorOrSelf<RsBinaryExpr>() }

        return when {
            declInitializer != null && writeUsages.isNotEmpty()
                || declInitializer == null && writeUsages.singleOrNull() == null -> {
                reportAmbiguousAssignment(project, editor, name, writeUsages.map { it!!.left }) //.left or what?
                null;
            }
            declInitializer == null -> writeUsages.single()?.right
            else -> declInitializer
        }
    }

    override fun canInlineElement(element: PsiElement?) = element is RsElement

    override fun isEnabledForLanguage(lang: Language?) = RsLanguage == lang

    private fun showErrorHint(project: Project, editor: Editor?, message: String) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            message,
            RefactoringBundle.message("inline.variable.title"),
            HelpID.INLINE_VARIABLE
        )
    }

    private fun reportAmbiguousAssignment(
        project: Project,
        editor: Editor?,
        name: String,
        assignments: Collection<PsiElement>
    ) {
        val key = if (assignments.isEmpty()) {
            "variable.has.no.initializer"
        } else {
            "variable.has.no.dominating.definition"
        }
        val message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message(key, name))
        showErrorHint(project, editor, message)
    }

    private fun highlightElements(project: Project, editor: Editor?, elements: List<PsiElement>) {
        if (editor == null || ApplicationManager.getApplication().isUnitTestMode) return

        val editorColorsManager = EditorColorsManager.getInstance()
        val searchResultsAttributes = editorColorsManager.globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
        val highlightManager = HighlightManager.getInstance(project)
        highlightManager.addOccurrenceHighlights(editor, elements.toTypedArray(), searchResultsAttributes, true, null)
    }

}
