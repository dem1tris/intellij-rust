/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.nullize
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

private const val WHERE_PREFIX = "where "

class RsGenericParameterInfoHandler : ParameterInfoHandler<RsTypeArgumentList, RsGenericPresentation> {

    var curParam = -1
        private set
    var hintText = ""
        private set

    override fun showParameterInfo(element: RsTypeArgumentList, context: CreateParameterInfoContext) {
        context.highlightedElement = null
        context.showHint(element, element.textRange.startOffset, this)
    }

    // todo: check: cursor moving on around <, bulb still present without text, IDEA-197133
    // todo: don't disappear in nested generic types
    override fun updateParameterInfo(parameterOwner: RsTypeArgumentList, context: UpdateParameterInfoContext) {
        curParam = ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.node, context.offset, RsElementTypes.COMMA)
        when {
            // todo: understand
            context.parameterOwner == null -> context.parameterOwner = parameterOwner

            // occurs in case of up-down cursor moving
            context.parameterOwner != parameterOwner -> context.removeHint()
        }
    }

    override fun updateUI(p: RsGenericPresentation, context: ParameterInfoUIContext) {
        hintText = p.presentText
        val (startOffset, endOffset) =
            if (hintText.startsWith(WHERE_PREFIX))
                0 to WHERE_PREFIX.length
            else
                p.getRange(curParam).startOffset to p.getRange(curParam).endOffset
        context.setupUIComponentPresentation(
            hintText,
            startOffset,
            endOffset,
            false, // grayed
            false,
            false, // define grayed part of args before highlight
            context.defaultParameterColor
        )
    }

    override fun getParametersForLookup(item: LookupElement?, context: ParameterInfoContext?): Array<Any>? = null

    override fun couldShowInLookup() = false

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): RsTypeArgumentList? =
        findExceptColonColon(context)

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): RsTypeArgumentList? {
        val parameterList = findExceptColonColon(context) ?: return null
        val parent = parameterList.parent
        val genericDeclaration = when (parent) {
            is RsMethodCall,
            is RsPath -> parent.reference?.resolve()
            else -> return null
        } as? RsGenericDeclaration ?: return null
        val typesWithBounds = genericDeclaration.typeParameters.nullize() ?: return null
        context.itemsToShow = listOfNotNull(
            RsGenericPresentation(typesWithBounds, false),
            RsGenericPresentation(typesWithBounds, true))
            .filterNot { it.presentText == "" }
            .toTypedArray()
        return parameterList
    }

    // to avoid hint on :: before <>
    private fun findExceptColonColon(context: ParameterInfoContext?): RsTypeArgumentList? {
        val element = context?.file?.findElementAt(context.editor.caretModel.offset) ?: return null
        if (element.elementType == RsElementTypes.COLONCOLON) return null
        return element.ancestorStrict() ?: return null
    }
}

/**
 * Stores the text representation and ranges for parameters
 */

class RsGenericPresentation(
    private val params: List<RsTypeParameter>,
    unrecognizedPartOfWhere: Boolean
) {
    val toText = if (!unrecognizedPartOfWhere) {
        params.map { param ->
            param.name ?: return@map ""
            val QSizedBound =
                if (!param.isSized)
                    listOf("?Sized")
                else
                    emptyList()
            val declaredBounds =
                param.bounds
                    // `?Sized`, if needed, in separate val, `Sized` shouldn't be shown
                    .filter {
                        it.bound.traitRef?.resolveToBoundTrait?.element?.isSizedTrait == false
                    }
                    .mapNotNull { it.bound.traitRef?.path?.text }
            val allBounds = QSizedBound + declaredBounds
            param.name + (allBounds.nullize()?.joinToString(prefix = ": ", separator = " + ") ?: "")
        }
    } else {
        val owner = params.getOrNull(0)?.parent?.parent as? RsGenericDeclaration
        val wherePreds = owner?.whereClause?.wherePredList.orEmpty()
            // retain specific preds
            .filterNot {
                params.contains((it.typeReference?.typeElement as? RsBaseType)?.path?.reference?.resolve())
            }
        wherePreds.map { it.text }
    }

    val presentText = toText.nullize()?.joinToString(
        prefix = if (!unrecognizedPartOfWhere) "" else WHERE_PREFIX
    ) ?: ""

    fun getRange(index: Int): TextRange {
        return if (index < 0 || index >= ranges.size)
            TextRange.EMPTY_RANGE
        else
            ranges[index]
    }

    private val ranges: List<TextRange> =
        if (!unrecognizedPartOfWhere) {
            toText.indices.map { calculateRange(it) }
        } else {
            emptyList()
        }

    private fun calculateRange(index: Int): TextRange {
        val start = toText.take(index).sumBy { it.length + 2 } // plus ", "
        return TextRange(start, start + toText[index].length)
    }
}
