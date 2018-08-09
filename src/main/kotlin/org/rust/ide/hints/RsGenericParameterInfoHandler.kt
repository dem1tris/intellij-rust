/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.typeParameters


class RsGenericParameterInfoHandler : ParameterInfoHandler<RsTypeArgumentList, List<RsTypeParameter>> {

    private var hintText: String = ""
    private var highlightStartOffset = -1
    private var highlightEndOffset = -1
    private var curParam = -1

    // todo: check offset
    override fun showParameterInfo(element: RsTypeArgumentList, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    // todo: check: cursor left than <, bulb still present without text
    override fun updateParameterInfo(parameterOwner: RsTypeArgumentList, context: UpdateParameterInfoContext) {
        // todo: fix: hint follows cursor even on lines below
        curParam = ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.node, context.offset, RsElementTypes.COMMA)
        // todo: understand
        when {
            context.parameterOwner == null -> context.parameterOwner = parameterOwner
            context.parameterOwner != parameterOwner -> context.removeHint()
        }
    }

    private fun ordinalIndexOrLength(str: String, substr: String, n: Int): Int {
        var m = n
        var pos = str.indexOf(substr)
        while (--m > 0 && pos != -1)
            pos = str.indexOf(substr, pos + 1)
        return when (pos) {
            -1 -> str.length
            else -> pos
        }
    }

    // todo: check: one-char hint isn't a bulb and hide text on the line
    override fun updateUI(params: List<RsTypeParameter>, context: ParameterInfoUIContext) {
        hintText = params.joinToString { it ->
            if (it.name != null)
                it.name + (it.typeParamBounds?.text ?: "")
            else
                ""
        }
        if (curParam >= 0 && curParam < params.size) {
            highlightStartOffset = if (curParam == 0)
                0
            else
                ordinalIndexOrLength(hintText, ",", curParam) + 1
            highlightEndOffset = ordinalIndexOrLength(hintText, ",", curParam + 1)
        } else {
            highlightStartOffset = -1
            highlightEndOffset = -1
        }
        // todo: Bulb appears not strictly above the cursor sometimes
        context.setupUIComponentPresentation(
            hintText,
            highlightStartOffset,
            highlightEndOffset,
            false, // grayed  // !context.isUIComponentEnabled, ???
            false,
            false, // Define grayed part of args before highlight
            context.defaultParameterColor
        )
    }

    // done
    override fun getParametersForLookup(item: LookupElement?, context: ParameterInfoContext?): Array<Any>? = null

    // done
    override fun couldShowInLookup() = false

    // done
    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): RsTypeArgumentList? =
        context.file.findElementAt(context.editor.caretModel.offset)?.ancestorStrict()

    //
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): RsTypeArgumentList? {
        val parameterList: RsTypeArgumentList? = context.file.findElementAt(context.editor.caretModel.offset)?.ancestorStrict()
        if (parameterList is RsTypeArgumentList) {
            val parent = parameterList.parent
            val genericOwner = when (parent) {
                is RsMethodCall -> parent
                is RsPath -> parent.parent
                else -> return null
            }
            val genericDeclaration = when (genericOwner) {
                is RsMethodCall -> genericOwner.reference.resolve()
                is RsPathExpr -> genericOwner.path.reference.resolve()
                is RsBaseType -> genericOwner.path?.reference?.resolve()
                else -> return null
            } as? RsGenericDeclaration ?: return null
            val typesWithBounds = genericDeclaration.typeParameters
            context.itemsToShow = arrayOf(typesWithBounds) // one-element array for one-line hint
            return parameterList
        }
        return null
    }
}
