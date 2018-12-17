/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.inline

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiReference
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.inline.InlineOptionsDialog
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.ext.RsNameIdentifierOwner
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.descendantOfTypeStrict

class RsInlineVariableDialog(
    private val decl: RsLetDecl,
    private val reference: PsiReference?,
    private val initializer: RsExpr,
    project: Project = decl.project
) : InlineOptionsDialog(project, true, decl) {
    override fun getBorderTitle(): String = RefactoringBundle.message("inline.variable.title")

    fun shouldBeShown() = EditorSettingsExternalizable.getInstance().isShowInlineLocalDialog

    private fun getInlineText(verb: String) = "Inline all references and $verb the variable"

    public override fun doAction() {
        invokeRefactoring(RsInlineVariableProcessor(
            project,
            decl,
            reference,
            initializer,
            inlineThisOnly = isInlineThisOnly,
            deleteDecl = !isInlineThisOnly && !isKeepTheDeclaration))
    }

    // better to use !!, then get NPE much deeper, isn't it?
    // TODO: perform possible -1 (too expensive to count)
    private val occurrencesNumber = initOccurrencesNumber(decl.pat!!.descendantOfTypeStrict<RsNameIdentifierOwner>());

    override fun getNameLabelText(): String {
        val occurrencesString = if (occurrencesNumber >= 0)
            "$occurrencesNumber ${StringUtil.pluralize("occurrence", occurrencesNumber)}"
        else
            ""
        val name = decl.pat?.descendantOfTypeStrict<RsNamedElement>()?.name ?: "<unnamed>"
        return "Variable '$name' - $occurrencesString"
    }

    override fun isInlineThis() = JavaRefactoringSettings.getInstance().INLINE_LOCAL_THIS

    override fun getInlineAllText() = getInlineText("remove")

    override fun getInlineThisText() = "Inline this reference and keep the variable"

    override fun getKeepTheDeclarationText() =
        if (occurrencesNumber == 1 && myInvokedOnReference) {
            null // to avoid duplicating semantics of "inline this and keep"
        } else {
            getInlineText("keep")
        }


    // TODO: why remove decl is inactive when single ref
    init {
        title = borderTitle
        myInvokedOnReference = reference != null
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
}
