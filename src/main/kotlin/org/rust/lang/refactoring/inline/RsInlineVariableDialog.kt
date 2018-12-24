/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.inline

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.inline.InlineOptionsDialog
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.ext.*

class RsInlineVariableDialog private constructor(
    private val decl: RsLetDecl,
    private val declaredElement: PsiElement,
    private val elementPat: RsPat,
    private val reference: PsiReference,
    private val occurrencesNumber: Int,
    private val initializer: RsExpr,
    project: Project = decl.project
) : InlineOptionsDialog(project, true, decl) {

    companion object {
        operator fun invoke(decl: RsLetDecl,
                            declaredElement: PsiElement,
                            reference: PsiReference,
                            initializer: RsExpr): RsInlineVariableDialog? {
            val pat = declaredElement.ancestorOrSelf<RsPat>() ?: return null
            val occurrencesNumber = initOccurrencesNumber(pat.descendantOfTypeStrict<RsNameIdentifierOwner>())
            return RsInlineVariableDialog(
                decl,
                declaredElement,
                pat,
                reference,
                occurrencesNumber,
                initializer
            )
        }
    }

    override fun getBorderTitle(): String = RefactoringBundle.message("inline.variable.title")

    fun shouldBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

    public override fun doAction() {
        invokeRefactoring(RsInlineVariableProcessor(
            project,
            decl,
            declaredElement,
            elementPat,
            reference,
            initializer,
            inlineThisOnly = isInlineThisOnly,
            deleteDecl = !isInlineThisOnly && !isKeepTheDeclaration))
    }

    override fun getNameLabelText(): String {
        val occurrencesString = if (occurrencesNumber >= 0)
            "- $occurrencesNumber ${StringUtil.pluralize("occurrence", occurrencesNumber)}"
        else
            ""
        val name = elementPat.descendantOfTypeStrict<RsNamedElement>()?.name ?: "<unnamed>"
        return "Variable '$name' $occurrencesString"
    }

    //override fun isInlineThis() = JavaRefactoringSettings.getInstance().INLINE_LOCAL_THIS
    override fun isInlineThis() = false

    override fun getInlineAllText() = getInlineText("remove")

    //override fun getInlineThisText() = "Inline this reference and keep the variable"
    override fun getInlineThisText(): String = RefactoringBundle.message("this.reference.only.and.keep.the.variable")

    override fun getKeepTheDeclarationText() =
        if (occurrencesNumber == 1 && myInvokedOnReference) {
            null // to avoid duplicating semantics of "inline this and keep"
        } else {
            getInlineText("keep")
        }


    init {
        title = borderTitle
        myInvokedOnReference = !elementPat.isAncestorOf(reference.element)
        println("myInvokedOnReference = $myInvokedOnReference")
        setPreviewResults(true) //TODO
        setDoNotAskOption(object : DialogWrapper.DoNotAskOption {
            override fun isToBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

            override fun setToBeShown(value: Boolean, exitCode: Int) {
                EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog = value
            }

            override fun canBeHidden() = true

            override fun shouldSaveOptionsOnCancel() = false

            override fun getDoNotShowMessage() = "Do not show in future"
        })
        init()
    }

    private fun getInlineText(verb: String) = "Inline all references and $verb the variable"
}
