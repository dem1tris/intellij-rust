/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsSmartCompletionContributor : CompletionContributor() {

    /**
     * The main contributor method that is supposed to provide completion variants to result, based on completion parameters.
     * The default implementation looks for [CompletionProvider]s you could register by
     * invoking [.extend] from your contributor constructor,
     * matches the desired completion type and [ElementPattern] with actual ones, and, depending on it, invokes those
     * completion providers.
     *
     *
     *
     * If you want to implement this functionality directly by overriding this method, the following is for you.
     * Always check that parameters match your situation, and that completion type ([CompletionParameters.getCompletionType]
     * is of your favourite kind. This method is run inside a read action. If you do any long activity non-related to PSI in it, please
     * ensure you call [com.intellij.openapi.progress.ProgressManager.checkCanceled] often enough so that the completion process
     * can be cancelled smoothly when the user begins to type in the editor.
     */
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.SMART) return
        val psiFactory = RsPsiFactory(parameters.editor.project!!)
        val context = ProcessingContext()
        val position = parameters.position
        println(parameters.editor.document.text)
        println(position.containingFile.text)
        println(position.textOffset)
        println(position.text)
        println(position.elementType)
        println("position = ${position}")
        println("position.parent = ${position.parent}")
        println("position.parent.parent = ${position.parent.parent}")
        println("position.parent.parent.prev = ${position.parent.parent.prevSibling}")
        println("position.parent.parent.next = ${position.parent.parent.nextSibling}")
        ProgressManager.checkCanceled()
        when {
            checkValueArgumentList(position) -> onValueArgumentList(position, context, result)

            checkReturnable(position) -> onReturnable(result)

            checkBoolean(position) -> onBoolean(result)
            checkLet(position) -> onLet(result)
        }


        // GlobalSearchScope
        // parameters.position
        // result.addElement(_.buildLiteral(psiFactory))
    }

    private fun RsStructItem.buildLiteral(factory: RsPsiFactory): LookupElement {
        val literal = factory.createStructLiteral(this.name!!)
        val body = literal.structLiteralBody
        return LookupElementBuilder.create(literal, this.name!!).withInsertHandler(object : InsertHandler<LookupElement> {
            /**
             * Invoked inside atomic action.
             */
            override fun handleInsert(context: InsertionContext?, item: LookupElement?) {
                val declaredFields = this@buildLiteral.namedFields
                declaredFields.map {
                    factory.createStructLiteralField(it.name!!, factory.createExpression("()"))
                }.forEach {
                    body.addAfter(it, body.lbrace)
                    ensureTrailingComma(body.structLiteralFieldList)
                }
            }
        })
    }

    private fun checkValueArgumentList(position: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(position, RsValueArgumentList::class.java, true,
            RsCondition::class.java) != null
    }

    private fun onValueArgumentList(position: PsiElement, context: ProcessingContext, result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("onVAL"))
        println("RsSmartCompletionContributor.onValueArgumentList")
    }

    private fun checkReturnable(position: PsiElement): Boolean {
        return position.ancestorOrSelf<RsRetExpr>() != null ||
            position.getPrevNonCommentSibling() is RsRetExpr ||
            position.rightLeaves.all {
                println("r l: ${it.elementType}")
                it.elementType == RsElementTypes.RBRACE
                    || it.elementType == TokenType.WHITE_SPACE
            }
    }

    private fun onReturnable(result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("onReturnable"))
        println("RsSmartCompletionContributor.onReturnable")
    }


    private fun checkBoolean(position: PsiElement): Boolean {
        println("RsSmartCompletionContributor.checkBoolean")
        val path = position.parent as? RsPath ?: return false
        val pexpr = path.parent as? RsPathExpr ?: return false
        return pexpr.ancestorStrict<RsCondition>() != null
    }

    private fun onBoolean(result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("onBoolean"))
        println("RsSmartCompletionContributor.onBoolean")
    }


    private fun checkLet(position: PsiElement): Boolean {
        println("RsSmartCompletionContributor.checkLet")
        val path = position.parent as? RsPath ?: return false
        val pexpr = path.parent as? RsPathExpr ?: return false
        val letDecl = pexpr.ancestorStrict<RsLetDecl>() ?: return false

        return true
    }

    private fun onLet(result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("onLet"))
        println("RsSmartCompletionContributor.onLet")
    }
}
