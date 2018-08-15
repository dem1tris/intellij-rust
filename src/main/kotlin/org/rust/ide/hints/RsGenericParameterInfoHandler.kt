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


class RsGenericParameterInfoHandler : ParameterInfoHandler<RsTypeArgumentList, RsGenericPresentation> {

    private var curParam = -1

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
        context.currentParameterIndex
        context.setupUIComponentPresentation(
            p.presentText,
            p.getRange(curParam).startOffset,
            p.getRange(curParam).endOffset,
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
        val typesWithBounds = genericDeclaration.typeParameters
        // one-element array for one-line hint
        context.itemsToShow = arrayOf(RsGenericPresentation(typesWithBounds))
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
    private val params: List<RsTypeParameter>
) {
    private val needQSizedBound = params.associate { param ->
        val declaredSized = param.bounds.any {
            it.bound.traitRef?.resolveToBoundTrait?.element?.isSizedTrait?.and(it.q == null) ?: false
        }
        // declared `?Sized`
        val declaredQSized = param.bounds.any {
            it.bound.traitRef?.resolveToBoundTrait?.element?.isSizedTrait?.and(it.q != null) ?: false
        }
        // one of supertraits is `Sized`
        val derivedSized = param.bounds
            // filter declared `Sized` and `?Sized`
            .filter {
                val trait = it.bound.traitRef?.resolveToBoundTrait ?: return@filter false
                trait.element.isSizedTrait.not()
            }
            .mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
            .flatMap { it.flattenHierarchy }
            // supertraits doesn't contain `Sized`
            .any { it.element.isSizedTrait }
        // `?Sized` only valid when `Sized` neither declared nor is supertrait
        val need = when {
            !declaredSized && !derivedSized && declaredQSized -> true
            else -> false
        }
        Pair(param, need)
    }

    /**
     * Could use TyTypeParameter.named(p: RsTypeParameter)
     * and getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>>
     * here, but if no `Sized` in Collection then impossible to determine
     * whether `?Sized` was declared or implicit `Sized` should be applied
     */

    val toText = params.map { param ->
        param.name ?: return@map ""
        val boundsTransitively =
            param.bounds
                .mapNotNull { it.bound.traitRef?.resolveToBoundTrait ?: return@mapNotNull null }
                .flatMap { it.flattenHierarchy } // to show supertraits
        val QSizedBound =
            if (needQSizedBound[param] == true)
                listOf("?Sized")
            else
                emptyList()
        val allBounds = QSizedBound + boundsTransitively
            // `?Sized`, if needed, in separate val, `Sized` shouldn't be shown
            .filterNot { it.element.isSizedTrait }
            .mapNotNull { it.element.identifier?.text ?: return@mapNotNull null }

        param.name + (allBounds.nullize()?.joinToString(prefix = ": ", separator = " + ") ?: "")
    }

    val presentText = toText.joinToString()

    fun getRange(index: Int): TextRange {
        return if (index < 0 || index >= params.size)
            TextRange.EMPTY_RANGE
        else
            ranges[index]
    }

    private val ranges: List<TextRange> = toText.indices.map { calculateRange(it) }

    private fun calculateRange(index: Int): TextRange {
        val start = toText.take(index).sumBy { it.length + 2 } // plus ", "
        return TextRange(start, start + toText[index].length)
    }
}
