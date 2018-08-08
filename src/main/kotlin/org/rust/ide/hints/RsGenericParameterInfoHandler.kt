/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.openapi.util.TextRange
import com.twelvemonkeys.lang.StringUtil
import org.rust.ide.utils.CallInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.types.type
import org.rust.stdext.buildList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sun.swing.StringUIClientPropertyKey
import kotlin.jvm.javaClass

class RsGenericParameterInfoHandler : ParameterInfoHandler<RsTypeArgumentList, RsTypeParameter> {
    fun newLogger(): Logger = LoggerFactory.getLogger(javaClass)
    val log = newLogger()

    var hintText: String = ""

    // done, todo: check offset
    override fun showParameterInfo(element: RsTypeArgumentList, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    //
    override fun updateParameterInfo(parameterOwner: RsTypeArgumentList, context: UpdateParameterInfoContext) {
        // val argIndex = findArgumentIndex
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun updateUI(p: RsTypeParameter, context: ParameterInfoUIContext) {
        System.err.println("${p.name}")
        System.err.println("${p.outerAttrList.map { it -> it.text }}")
        System.err.println("${p.typeParamBounds?.text}") //polyboundList?.map {it -> it.bound.text}}")
        hintText = p.name + p.outerAttrList.map { it -> it.text }
        // Bulb appears not strictly above the cursor sometimes
        val highlightEndOffset = hintText.length
        context.setupUIComponentPresentation(
            hintText,
            0,
            highlightEndOffset,
            false, // grayed  // !context.isUIComponentEnabled, ???
            false,
            false, // Define grayed part of args before highlight
            context.defaultParameterColor
        )
    }

    // ???
    override fun getParametersForLookup(item: LookupElement?, context: ParameterInfoContext?): Array<Any>? = null

    // ???
    override fun couldShowInLookup() = false

    // done
    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext) =
        context.file.findElementAt(context.editor.caretModel.offset) as? RsTypeArgumentList

    /**
     * From ParameterInfoHandler interface:
     * "Find element for parameter info should also set ItemsToShow in context and may set highlighted element"
     */

    //
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): RsTypeArgumentList? {
        val parameterList: RsTypeArgumentList? = context.file.findElementAt(context.editor.caretModel.offset)?.ancestorStrict()
        if (parameterList is RsTypeArgumentList) {
            val ancestor = parameterList.parent
            System.err.println("parent: ${ancestor}")
            System.err.println("parent.parent: ${ancestor.parent}")
            System.err.println("parent.parent.parent: ${ancestor.parent.parent}")
            when (ancestor) {
                is RsMethodCall -> {
                    val func = ancestor.reference.resolve() ?: return null
                    val typeParams = (func as? RsGenericDeclaration)?.typeParameterList ?: return null
                    val typeParamsArray = typeParams.typeParameterList.toTypedArray()
                    context.itemsToShow = typeParamsArray
                    System.err.println("MethodCall, done")
                }
                else -> {
                    @Suppress("NAME_SHADOWING")
                    val ancestor = ancestor.parent?.parent
                    when (ancestor) {
                        is RsCallExpr -> {
                            System.err.println("is CallExpr")
                        }
                        is RsTypeReference -> {
                            System.err.println("isTypeRef")
                        }
                        else -> {
                            TODO("Not implemented, p.p.p: ${ancestor?.parent?.parent}")
                        }
                    }
                }
            }



            return parameterList
        }
        return null
    }
    /*private fun findArgumentIndex(place: PsiElement): Int {
        val callArgs = place.ancestorStrict<RsValueArgumentList>() ?: return RsParameterInfoHandler.INVALID_INDEX
        val descr = RsArgumentsDescription.findDescription(callArgs) ?: return RsParameterInfoHandler.INVALID_INDEX
        var index = -1
        if (descr.arguments.isNotEmpty()) {
            index += generateSequence(callArgs.firstChild, { c -> c.nextSibling })
                .filter { it.text == "," }
                .count({ it.textRange.startOffset < place.textRange.startOffset }) + 1
            if (index >= descr.arguments.size) {
                index = -1
            }
        }
        return index
    }*/

    /**
     * Holds information about generic types
     */
}
