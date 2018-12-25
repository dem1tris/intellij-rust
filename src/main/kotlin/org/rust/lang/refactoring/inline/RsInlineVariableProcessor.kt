/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.inline

import com.intellij.lang.findUsages.DescriptiveNameUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.runWriteCommandAction
import org.rust.lang.core.psi.RsElementTypes

class RsInlineVariableProcessor(
    private val project: Project,
    private val declaration: RsLetDecl,
    private val declaredElement: PsiElement,
    private val elementPat: RsPat,
    private val reference: PsiReference, // RsReference?
    private val initializer: RsExpr,
    private val inlineThisOnly: Boolean,
    private val deleteDecl: Boolean
) : BaseRefactoringProcessor(project) {
    override fun findUsages(): Array<UsageInfo> {
        //TODO: return error upper
        val invokedOnReference = !elementPat.isAncestorOf(reference.element)
        if (inlineThisOnly && invokedOnReference) return arrayOf(UsageInfo(reference))
        val usages = runReadAction {
            val searchScope = GlobalSearchScope.fileScope(declaration.containingFile)
            ReferencesSearch.search(declaredElement, searchScope)
        }
        return usages.map(::UsageInfo).toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        // TODO: deal with conditioned non-exhaustive assignment
        // TODO: process take reference usages
        project.runWriteCommandAction {
            println("usages.size = ${usages.size}")

            val declaredTuple = declaration.descendantOfTypeStrict<RsPatTup>()
            val replacer = if (declaredTuple != null) {
                initializer as RsTupleExpr //TODO: return error upper
                val dotdot = declaredTuple.dotdot

                val expr: RsExpr;
                val copy: PsiElement;
                if (dotdot == null || dotdot.startOffsetInParent > elementPat.startOffsetInParent) {
                    expr = initializer.exprList[declaredTuple.patList.indexOf(elementPat)] ?: return@runWriteCommandAction
                } else {
                    // righter than dotdot, so count indices from end of tuple
                    expr = initializer.exprList.reversed()[declaredTuple.patList.reversed().indexOf(elementPat)]
                        ?: return@runWriteCommandAction
                }
                copy = expr.copy()

                val isRightmost = expr.rightSiblings.asSequence().filter { it is RsExpr }.count() == 0
                if (isRightmost) {
                    println("rightmost")
                    val leftPatSibling = elementPat
                        .leftSiblings
                        .asSequence()
                        .filter { it is RsExpr }
                        .first()
                    leftPatSibling.rightSiblings.asSequence().takeWhile { it !is RsExpr }.forEach { it.delete() }
                    //expr.leftSiblings.asSequence().forEach { println("left sibl: $it") }
                    //expr.leftSiblings.asSequence().takeWhile { println(it.text); it !is RsExpr }.forEach { it.delete() }
                } else {
                    println("!rightmost")
                    expr.rightSiblings.asSequence().forEach { println("rigt sibl: $it") }
                    expr.rightSiblings.asSequence().takeWhile { it !is RsExpr }.forEach { it.delete() }
                }
                expr.delete()
                copy

            } else {
                initializer
            } as RsExpr
            println("replacer = ${replacer.text}")

            usages.asIterable().forEach {
                val replaceable = it.element?.ancestorOrSelf<RsPathExpr>()
                if (it.isValid && (replaceable?.isValid == true)) {
                    val parentheses = RsPsiFactory(project).tryCreatePat("(a,)") as RsPatTup;
                    if (replacer is RsBinaryExpr) {
                        replaceable.parent.addBefore(parentheses.lparen, replaceable)
                        replaceable.parent.addAfter(parentheses.rparen, replaceable)

                    }
                    replaceable.replace(replacer.copy())
                }
            }
            if (deleteDecl) {
                if (declaredTuple == null || (declaredTuple.patList.size == 1 && declaredTuple.dotdot == null)) {
                    declaration.delete();
                } else {

                    val isRightmost = elementPat.rightSiblings.asSequence().filter { it is RsPat }.count() == 0
                    if (isRightmost) {
                        println("rightmost")
                        val leftPatSibling = elementPat
                            .leftSiblings
                            .asSequence()
                            .filter { it is RsPat || it.elementType == RsElementTypes.DOTDOT}
                            .first()
                        leftPatSibling.rightSiblings.asSequence().takeWhile { it !is RsPat }.forEach { it.delete() }
                    } else {
                        println("!rightmost")
                        elementPat.rightSiblings.asSequence().forEach { println("rigt sibl: $it") }
                        elementPat.rightSiblings.asSequence().takeWhile { it !is RsPat }.forEach { it.delete() }
                    }
                    elementPat.delete()

                    if (declaredTuple.patList.size == 1 && declaredTuple.dotdot == null) {
                        declaredTuple.replace(declaredTuple.patList.first())
                    }
                }
            }
        }
    }

    private val commandName = "Inlining variable '${DescriptiveNameUtil.getDescriptiveName(declaration)}'"

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
