/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.runWriteCommandAction

class RsInlineVariableHandler : InlineActionHandler() {
    override fun inlineElement(project: Project, editor: Editor, element: PsiElement) {
        println("RsInlineVariableHandler.inlineElement")
        println(element.textOffset)
        if (element !is RsElement) return;
        println("is RsElement")


        val decl = element.ancestorStrict<RsLetDecl>() ?: TODO("decl")
        val references = ReferencesSearch.search(element).findAll()
        val initializer = extractInitializer(project, editor, decl, references);


        if (references.count() == 0) {
            val name = decl.pat?.descendantOfTypeStrict<RsPatBinding>()?.identifier?.text ?: TODO("unnamed")
            return showErrorHint(project, editor, "Variable '$name' is never used")
        }
        println("start")
        project.runWriteCommandAction {
            references.forEach {
                println(it.element.ancestorStrict<RsBinaryExpr>()?.text)
                it.element.replace(initializer!!)
                // TODO: notNullChild somehow throws
                // TODO: wrap complex initializers with parenthesis
                // TODO: forbid inline for unpacking assignment
                // TODO: delete declaration
                // TODO: deal with conditioned non-exhaustive assignment

                println("replaced")
            }
        }

    }

    private fun extractInitializer(
        project: Project,
        editor: Editor,
        decl: RsLetDecl,
        refs: Collection<PsiReference>
    ): RsExpr? {
        val declInitializer = decl.expr
        val name = decl.pat?.descendantOfTypeStrict<RsPatBinding>()?.identifier?.text ?: TODO("unnamed")
        val writeUsages = refs
            .filter {
                val expr = it.element.ancestorOrSelf<RsBinaryExpr>()
                expr?.left?.isAncestorOf(it.element) == true && expr.isAssignBinaryExpr
            }.map { it.element.ancestorOrSelf<RsBinaryExpr>() }

        if (declInitializer != null) {
            if (writeUsages.isNotEmpty()) {
                reportAmbiguousAssignment(project, editor, name, writeUsages.map { it!!.left }) //.left or what?
                return null;
            }
            return declInitializer
        } else {
            if (writeUsages.singleOrNull() == null) {
                reportAmbiguousAssignment(project, editor, name, writeUsages.map { it!!.left })
                return null;
            }
            return writeUsages.single()!!.right
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
}
