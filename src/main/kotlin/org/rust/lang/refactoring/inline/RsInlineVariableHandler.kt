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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsInlineVariableHandler : InlineActionHandler() {
    override fun inlineElement(project: Project, editor: Editor, element: PsiElement) {
        println("RsInlineVariableHandler.inlineElement")
        println(element.textOffset)
        if (element !is RsElement) return;
        println("is RsElement")

        val declaration = element.ancestorStrict<RsLetDecl>() ?: return
        val pat = element.ancestorOrSelf<RsPat>() ?: return
        val name = pat.descendantOfTypeStrict<RsPatBinding>()?.identifier?.text ?: return
        val underCaretRef = TargetElementUtil.findReference(editor, editor.caretModel.offset) ?: return

        val references = ReferencesSearch.search(element).findAll()
        val initializer = extractInitializer(project, editor, declaration, references) ?: return
        if (!initializer.isValid) return
        val dialog =
            RsInlineVariableDialog(declaration, element, underCaretRef, initializer) ?: return

        if (declaration.descendantOfTypeStrict<RsPatTup>() != null && initializer !is RsTupleExpr) {
            return showErrorHint(project, editor,
                "Cannot extract initializer for variable '$name' with tuple-unpacking assignment")
        }

        if (references.count() == 0) {
            return showErrorHint(project, editor, "Variable '$name' is never used")
        }

        val refsInOriginalFile = references
            .filter { it.element.containingFile == declaration.containingFile }
            .map { it.element }
        highlightElements(project, editor, refsInOriginalFile)

        if (/*withPrompt && */!ApplicationManager.getApplication().isUnitTestMode && dialog.shouldBeShown()) {
            dialog.show()
            if (!dialog.isOK /*&& hasHighlightings*/) {
                val statusBar = WindowManager.getInstance().getStatusBar(declaration.project)
                statusBar?.info = RefactoringBundle.message("press.escape.to.remove.the.highlighting")
            }
        } else {
            dialog.doAction()
            //TODO: remember last chosed option
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
            }.mapNotNull { it.element.ancestorOrSelf<RsBinaryExpr>() }

        return when {
            declInitializer != null && writeUsages.isNotEmpty()
                || declInitializer == null && writeUsages.singleOrNull() == null -> {
                reportAmbiguousAssignment(project, editor, name, writeUsages.map { it.left }) // TODO: .left or what?
                null;
            }
            declInitializer == null -> writeUsages.single().right
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
            "refactoring.inlineVariable"
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
        highlightManager.addOccurrenceHighlights(
            editor,
            elements.toTypedArray(),
            searchResultsAttributes,
            true,
            null
        )
    }

}