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
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ensureTrailingComma
import org.rust.lang.core.psi.ext.namedFields

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
        ProgressManager.checkCanceled()

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
}
