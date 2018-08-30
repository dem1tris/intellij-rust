/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.lang.parameterInfo.ParameterInfoUtils
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.rust.ide.formatter.impl.CommaList
import org.rust.ide.formatter.processors.removeTrailingComma
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.lang.core.resolve.processFunctionDeclarations
import org.rust.lang.core.resolve.processPathResolveVariants
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyFunction
import org.rust.lang.core.types.type

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
    // todo: add single completion variant <space> in case of e.g. `return/*caret*/` invocation
    // todo: call .checkCanceled more often
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.SMART) return
        val context = ProcessingContext()
        val position = parameters.position
        //println(parameters.editor.document.text)
        //println(position.containingFile.text)
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
            checkValueArgumentList(position) -> onValueArgumentList(parameters, context, result)
            checkReturnable(position) -> onReturnable(parameters, context, result)
            checkBoolean(position) -> onBoolean(parameters, context, result)
            checkLet(position) -> onLet(parameters, context, result)
        }


        // GlobalSearchScope
        // parameters.position
        // result.addElement(_.buildLiteral(psiFactory))
    }

    // todo: as template
    private fun RsStructItem.buildLiteral(factory: RsPsiFactory): LookupElement {
        return LookupElementBuilder
            .create(factory.createStructLiteral(this.name!!), this.name!!)
            .withTailText(" { ... }")
            .bold()
            .withIcon(RsIcons.STRUCT)
            .withInsertHandler(StructHandler(this))
    }

    private fun RsStructItem.newCalls(factory: RsPsiFactory): List<LookupElement> {
        val newFuncs = this.searchForImplementations().forEach {

        }
        val args: List<RsExpr> = listOf()
        return listOf(LookupElementBuilder.create(
            factory.createAssocFunctionCall(this.name!!, "new", args),
            "${this.name}::new(${args.joinToString()})")
            .withIcon(RsIcons.ASSOC_FUNCTION))
    }

    private fun checkValueArgumentList(position: PsiElement): Boolean {
        return PsiTreeUtil.getParentOfType(position, RsValueArgumentList::class.java, true,
            RsCondition::class.java) != null
    }

    private fun onValueArgumentList(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        println("RsSmartCompletionContributor.onValueArgumentList")
        val path = parameters.position.ancestorStrict<RsPath>() ?: TODO()
        val pexpr = path.ancestorStrict<RsPathExpr>() ?: TODO()
        val parameterList = pexpr.ancestorStrict<RsValueArgumentList>() ?: TODO()
        val index = ParameterInfoUtils.getCurrentParameterIndex(
            parameterList.node,
            parameters.offset,
            RsElementTypes.COMMA)
        val call = parameterList.parent
        val function = when (call) {
            is RsCallExpr -> (call.expr as? RsPathExpr)?.path?.reference?.resolve()
            is RsMethodCall -> call.reference.resolve()
            else -> null
        } ?: TODO()
        val typeSet = setOf<Ty>().toMutableSet()
        if (function is RsFunction) {
            function.valueParameters.filterIndexed { ind, _ ->
                if (function.selfParameter == null) index == ind
                else index == ind - 1
            }.map {
                it.typeReference?.type ?: TODO("NO TY")
            }.map {
                typeSet.add(it)
            }
        } else {
            val tyFunction = (call as? RsCallExpr)?.expr?.type as? TyFunction ?: TODO()
            val type = tyFunction.paramTypes.getOrNull(index) ?: TODO()
            typeSet.add(type)
        }

        val structs = typeSet.mapNotNull { ((it as? TyAdt)?.item as? RsStructItem) }
        var variants =
            collectCompletionVariants(
                { processPathResolveVariants(ImplLookup.relativeTo(path), path, true, it) },
                Filter(typeSet))
                .map {
                    if (it.psiElement is RsStructItem && !structs.isEmpty()) {
                        val struct = structs.find { item ->
                            item == (it.psiElement as? RsStructItem)
                        }
                        if (struct != null) LookupElementDecorator.withInsertHandler(it, StructHandler(struct))
                        else it
                    } else it
                }
        variants.forEach { result.addElement(it) }
        result.addElement(LookupElementBuilder.create("onVAL"))
    }

    private fun checkReturnable(position: PsiElement): Boolean {
        println("RsSmartCompletionContributor.checkReturnable")
        val path = position.parent as? RsPath ?: return false
        val pexpr = path.parent as? RsPathExpr ?: return false
        val retExpr = pexpr.ancestorStrict<RsRetExpr>()
        val function = pexpr.ancestorStrict<RsFunction>() ?: return false
        return position.rightLeaves
            .filter { function.isAncestorOf(it) }
            .all {
                println(it.elementType)
                it.elementType == RsElementTypes.RBRACE
                    || it.elementType == TokenType.WHITE_SPACE
                    || RS_COMMENTS.contains(it.elementType)
            } || retExpr != null
    }

    private fun onReturnable(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        println("RsSmartCompletionContributor.onReturnable")
        val path = parameters.position.ancestorStrict<RsPath>() ?: return
        val retType = parameters.position.ancestorStrict<RsFunction>()?.returnType ?: return
        val typeSet = setOf(retType).toMutableSet()

        val struct = (retType as? TyAdt)?.item as? RsStructItem
        // todo: need collect assoc functions
        var variants =
            collectCompletionVariants({ processPathResolveVariants(ImplLookup.relativeTo(path), path, true, it) }, Filter(typeSet))
                .map {
                    if (it.psiElement is RsStructItem && struct != null) LookupElementDecorator.withInsertHandler(it, StructHandler(struct))
                    else it
                }

        variants += collectCompletionVariants({ processFunctionDeclarations(ImplLookup.relativeTo(path), retType, it) })

        variants.forEach { result.addElement(it) }
        result.addElement(LookupElementBuilder.create("onReturnable"))
    }


    private fun checkBoolean(position: PsiElement): Boolean {
        println("RsSmartCompletionContributor.checkBoolean")
        val path = position.parent as? RsPath ?: return false
        val pexpr = path.parent as? RsPathExpr ?: return false
        return pexpr.ancestorStrict<RsCondition>() != null
    }

    private fun onBoolean(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("onBoolean"))
        println("RsSmartCompletionContributor.onBoolean")
    }


    private fun checkLet(position: PsiElement): Boolean {
        println("RsSmartCompletionContributor.checkLet")
        val path = position.parent as? RsPath ?: return false
        val pexpr = path.parent as? RsPathExpr ?: return false
        val letDecl = PsiTreeUtil.getParentOfType(pexpr, RsLetDecl::class.java, true,
            RsCondition::class.java) ?: return false

        return true
    }

    private fun onLet(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("onLet"))
        println("RsSmartCompletionContributor.onLet")
        val a = RsLangItemIndex
    }


    private class Filter(val typeSet: Set<Ty>) : (RsElement) -> Boolean {
        private fun Ty.suite(typeSet: Set<Ty>): Boolean {
            if (!typeSet.contains(this)) return false
            if (this is TyAdt && typeSet.none { this.item == (it as? TyAdt)?.item }) return false
            return true
        }

        override fun invoke(element: RsElement) = { it: RsElement ->
            println("cv ${it.elementType}:$it:${(it as? RsFunction)?.name ?: ""}")
            when {
                it is RsStructItem && it.declaredType.suite(typeSet) -> true
                it is RsPatBinding && it.type.suite(typeSet) -> true
                it is RsFunction && it.returnType.suite(typeSet) -> true
                else -> false
            }
        }.invoke(element)
    }
}

class StructHandler(val struct: RsStructItem) : InsertHandler<LookupElement?> {
    override fun handleInsert(context: InsertionContext, item: LookupElement?) {
        val factory = RsPsiFactory(context.project)
        val offset = context.editor.caretModel.offset
        // `S` as RsPathExpr
        val pathExpr = context.file.findElementAt(offset)
            ?.prevSibling as? RsPathExpr ?: return
        println("pathExpr = ${pathExpr}")
        val declaredFields = struct.namedFields
        val forceMultiline = declaredFields.size > 2
        var firstAdded: RsStructLiteralField? = null
        if (!declaredFields.isEmpty()) {
            pathExpr.parent.addAfter(factory.createStructLiteral("Dummy").structLiteralBody, pathExpr)
            pathExpr.parent.addAfter(factory.createWhitespace(" "), pathExpr)
        }

        // RsStructLiteralBody in `S {  }`
        val inserted = context.file.findElementAt(offset)
            ?.nextSibling as? RsStructLiteralBody ?: return
        declaredFields.map {
            println("fld map")
            factory.createStructLiteralField(it.name!!, factory.createExpression("()"))
        }.forEach {
            println("fld foreach")
            val added = inserted.addBefore(it, inserted.rbrace) as RsStructLiteralField
            if (firstAdded == null) {
                firstAdded = added
            }
            ensureTrailingComma(inserted.structLiteralFieldList)
        }

        if (forceMultiline) {
            inserted.addAfter(factory.createNewline(), inserted.lbrace)
        }
        CommaList.forElement(inserted.elementType)?.removeTrailingComma(inserted)

        // `field: (/*caret*/)`
        if (firstAdded != null) {
            context.editor.caretModel.moveToOffset(firstAdded?.expr!!.textOffset + 1)
        }
    }
}
