/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsTypeArgumentList
import org.rust.lang.core.psi.ext.ancestorStrict

// RsArgumentsDescription from RsParameterInfoHandler
class RsGenericParameterInfoHandler : ParameterInfoHandler<PsiElement, RsArgumentsDescription> {

    var hintText: String = ""

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        context.itemsToShow = arrayOf(RsArgumentsDescription(arrayOf("Dummy hint")))
        context.showHint(element, element.textRange.startOffset, this);
        // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun updateParameterInfo(parameterOwner: PsiElement, context: UpdateParameterInfoContext) {
        // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun updateUI(p: RsArgumentsDescription?, context: ParameterInfoUIContext) {
        // Bulb appears not strictly above the cursor sometimes
        if (p == null) {
            context.isUIComponentEnabled = false
            return
        }
        val range = p.getArgumentRange(context.currentParameterIndex)
        hintText = p.presentText
        context.setupUIComponentPresentation(
            hintText,
            range.startOffset,
            range.endOffset,
            !context.isUIComponentEnabled,
            false,
            false,
            context.defaultParameterColor
        )
        // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getParametersForLookup(item: LookupElement?, context: ParameterInfoContext?): Array<Any>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun couldShowInLookup() = true

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext) =
        context.file.findElementAt(context.editor.caretModel.offset)

    /**
     * From ParameterInfoHandler interface:
     * "Find element for parameter info should also set ItemsToShow in context and may set highlighted element"
     * ???
      */

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val contextElement = context.file.findElementAt(context.editor.caretModel.offset) ?: return null
        return findElementForParameterInfo(contextElement)
    }

    /**
     * Returns RsTypeArgumentList, if it is a parent of contextElement, otherwise returns null
     */
    private fun findElementForParameterInfo(contextElement: PsiElement) =
        contextElement.ancestorStrict<RsTypeArgumentList>()
}
