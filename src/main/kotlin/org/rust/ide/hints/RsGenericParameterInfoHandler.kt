/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.utils.CallInfo
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsTypeArgumentList
import org.rust.lang.core.psi.RsValueArgumentList
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.types.type
import org.rust.stdext.buildList

// RsArgumentsDescription from RsParameterInfoHandler
class RsGenericParameterInfoHandler : ParameterInfoHandler<PsiElement, RsTypesDescription> {

    var hintText: String = ""

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        if (element !is RsTypeArgumentList) return
        val valueArgList = element.parent.parent.nextSibling as? RsValueArgumentList ?: return
        val argsDescr = RsTypesDescription.findDescriptionInExpr(valueArgList) ?: return
        context.itemsToShow = arrayOf(argsDescr) //arrayOf(RsArgumentsDescription(arrayOf("Dummy hint")))
        context.showHint(element, element.textRange.startOffset, this);
    }

    override fun updateParameterInfo(parameterOwner: PsiElement, context: UpdateParameterInfoContext) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun updateUI(p: RsTypesDescription?, context: ParameterInfoUIContext) {
        // Bulb appears not strictly above the cursor sometimes
        if (p == null) {
            context.isUIComponentEnabled = false
            return
        }
        val range = p.getArgumentRange(context.currentParameterIndex)
        hintText = p.presentText
        System.err.println(range) // dbg
        System.err.println(range.startOffset.toString() + "," + range.endOffset) // dbg
        p.arguments.asIterable().forEach({System.err.println(it)}) // dbg
        context.setupUIComponentPresentation(
            hintText,
            range.startOffset,
            range.endOffset,
            false, // grayed  // !context.isUIComponentEnabled, ???
            false,
            false, // Define grayed part of args before highlight
            context.defaultParameterColor
        )
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


/**
 * Holds information about generic types
 */

class RsTypesDescription(
    val arguments: Array<String>
) {
    fun getArgumentRange(index: Int): TextRange {
        if (index < 0 || index >= arguments.size) return TextRange.EMPTY_RANGE
        val start = arguments.take(index).sumBy { it.length + 2 }
        return TextRange(start, start + arguments[index].length)
    }

    val presentText = if (arguments.isEmpty()) "<no arguments>" else arguments.joinToString(", ")

    companion object {
        /**
         * Finds declaration of the func/method and creates description of its arguments
         */
        fun findDescriptionInExpr(args: RsValueArgumentList): RsTypesDescription? {
            args.exprList.forEach({ System.err.println(it.type)}) // dbg
            val types = buildList<String> {
                addAll(args.exprList.map { "${it.type}" })
            }
            return RsTypesDescription(types.toTypedArray())
        }
        fun findDescription(args: RsValueArgumentList): RsArgumentsDescription? {
            // CALL_EXPR for fun or method with explicit &self || METHOD_CALL for method
            val call = args.parent
            val callInfo = when (call) {
                is RsCallExpr -> CallInfo.resolve(call)
                is RsMethodCall -> CallInfo.resolve(call)
                else -> null
            } ?: return null
            val params = buildList<String> {
                if (callInfo.selfParameter != null && call is RsCallExpr) {
                    add(callInfo.selfParameter)
                }
                addAll(callInfo.parameters.map { "${it.pattern}: ${it.type}" })
            }
            // params.asIterable().forEach({ System.err.println(it)}) // dbg
            return RsArgumentsDescription(params.toTypedArray())
        }
    }
}
