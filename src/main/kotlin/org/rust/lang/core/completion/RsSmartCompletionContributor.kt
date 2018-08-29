/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.nullize
import org.rust.ide.formatter.impl.CommaList
import org.rust.ide.formatter.processors.removeTrailingComma
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.lang.core.resolve.processMethodCallExprResolveVariants
import org.rust.lang.core.resolve.processPathResolveVariants
import org.rust.lang.core.types.ty.TyAdt
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
        result.addElement(LookupElementBuilder.create("onVAL"))
        println("RsSmartCompletionContributor.onValueArgumentList")
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
        // TODO fix me
        val path = parameters.position.ancestorStrict<RsPath>() ?: return
        val type = parameters.position.ancestorStrict<RsFunction>()?.returnType as? TyAdt ?: return
        val struct = type.item as? RsStructItem ?: return
        var variants = collectCompletionVariants({ processPathResolveVariants(ImplLookup.relativeTo(path), path, true, it) },
            {
                println("cv ${it.elementType}:$it")
                return@collectCompletionVariants when {
                    (it as? RsStructItem)?.name == struct.name -> true
                    it is RsPatBinding && it.type == type -> true
                    it is RsFunction /*&& it.isAssocFn && it.retType as? TyAdt == type*/ -> true
                    else -> false
                }
            })
        variants += collectCompletionVariants( {processMethodCallExprResolveVariants(ImplLookup.relativeTo(struct), type, it)} )
        variants.map { if (it.psiElement is RsStructItem) LookupElementDecorator.withInsertHandler(it, StructHandler(struct)) else it }
        .forEach { result.addElement(it) }
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
