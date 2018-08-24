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
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
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
        println(position.text)
        ProgressManager.checkCanceled()
        when {
            position.ancestorStrict<RsValueArgumentList>() != null
            -> onValueArgumentList(position, context, result)

            position.ancestorOrSelf<RsRetExpr>() != null ||
                position.getPrevNonCommentSibling() is RsRetExpr ||
                position.rightLeaves.all {
                    println("r l: ${it.elementType}")
                    it.elementType == RsElementTypes.RBRACE
                        || it.elementType == TokenType.WHITE_SPACE
                }
            -> onReturnable(result)

            position.leftSiblings.take(5).map { println("bol ls $it"); it }.count() == 2 ||//getPrevNonCommentSibling()?.elementType == RsElementTypes.IF ||
                position.ancestorStrict<RsWhileExpr>() != null
            -> onBoolean(result)

            position.leftSiblings.count() == 0 -> println("EmptySibl")
            position.leftSiblings.toSet()
                .map { println("let ${it.elementType}"); it.elementType }
                .containsAll(listOf(RsElementTypes.EQ, RsElementTypes.LET_DECL))
            -> onLet(result)
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

    private fun onValueArgumentList(position: PsiElement, context: ProcessingContext, result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("onValueArgumentList"))
        println("RsSmartCompletionContributor.onValueArgumentList")
    }

    private fun onReturnable(result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("onReturnable"))
        println("RsSmartCompletionContributor.onReturnable")
    }

    private fun onBoolean(result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("onBoolean"))
        println("RsSmartCompletionContributor.onBoolean")
    }

    private fun onLet(result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("onLet"))
        println("RsSmartCompletionContributor.onLet")
    }
}
