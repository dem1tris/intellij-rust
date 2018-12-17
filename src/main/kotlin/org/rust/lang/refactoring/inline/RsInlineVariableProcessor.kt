/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.inline

import com.intellij.lang.findUsages.DescriptiveNameUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.ext.contextualFile
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.openapiext.runWriteCommandAction
import org.rust.stdext.collectionSizeOrDefault

class RsInlineVariableProcessor(
    private val project: Project,
    private val declaration: RsLetDecl,
    private val reference: PsiReference?, // RsReference?
    private val initializer: RsExpr,
    private val inlineThisOnly: Boolean,
    private val deleteDecl: Boolean
) : BaseRefactoringProcessor(project) {
    override fun findUsages(): Array<UsageInfo> {
        if (inlineThisOnly && reference != null) return arrayOf(UsageInfo(reference))
        val usages = runReadAction {
            val searchScope = GlobalSearchScope.fileScope(declaration.containingFile)
            ReferencesSearch.search(declaration.pat!!.firstChild, searchScope)
        }
        println(usages.collectionSizeOrDefault(777))
        return usages.map(::UsageInfo).toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        // TODO: notNullChild somehow throws
        // TODO: deal with conditioned non-exhaustive assignment
        project.runWriteCommandAction {
            println("usages.size = ${usages.size}")
            usages.asIterable().forEach {
                println(it.element?.text)
                if (it.isValid) {
                    print("valid")
                    it.element?.replace(initializer.copy())
                    //TODO: wrap binary expressions into parentheses
                }
            }
            if (deleteDecl) {
                declaration.delete();
            }
        }
    }

    private val commandName = "Inlining variable ${DescriptiveNameUtil.getDescriptiveName(declaration)}"

    override fun getCommandName() = commandName

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return object : UsageViewDescriptor {
            override fun getCommentReferencesText(usagesCount: Int, filesCount: Int) =
                RefactoringBundle.message("comments.elements.header", UsageViewBundle.getOccurencesString(usagesCount, filesCount))

            override fun getCodeReferencesText(usagesCount: Int, filesCount: Int) =
                RefactoringBundle.message("invocations.to.be.inlined", UsageViewBundle.getReferencesString(usagesCount, filesCount))

            // TODO: fix
            override fun getElements() = arrayOf(declaration)

            override fun getProcessedElementsHeader() = "Variable to inline"
        }
    }
}
