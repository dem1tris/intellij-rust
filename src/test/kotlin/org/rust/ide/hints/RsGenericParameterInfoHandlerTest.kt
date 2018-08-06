/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import org.rust.lang.RsTestBase


/**
 * Tests for RustGenericParameterInfoHandler
 */
class RsGenericParameterInfoHandlerTest : RsTestBase() {
    /*fun testFnNoArgs() = checkByText("""
        fn foo() {}
        fn main() { foo(<caret>); }
    """, "<no arguments>", -1)*/

    /*fun testFnNoArgs() = checkByText("""
        fn foo() {}
        fn main() { foo(<caret>); }
    """, "<no arguments>", -1)*/

    private fun checkByText(code: String, hint: String, index: Int) {
        myFixture.configureByText("main.rs", code)
        val handler = RsGenericParameterInfoHandler()
        val createContext = MockCreateParameterInfoContext(myFixture.editor, myFixture.file) // ...Generic... ???

        // Check hint
        val elt = handler.findElementForParameterInfo(createContext)
        if (hint.isNotEmpty()) {
            elt ?: throw AssertionFailedError("Hint not found")
            handler.showParameterInfo(elt, createContext)
            val items = createContext.itemsToShow ?: throw AssertionFailedError("Parameters are not shown")
            if (items.isEmpty()) throw AssertionFailedError("Parameters are empty")
            val context = MockParameterInfoUIContext(elt)
            handler.updateUI(items[0] as RsArgumentsDescription, context)
            TestCase.assertEquals(hint, handler.hintText)

            // Check parameter index
            val updateContext = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
            val element = handler.findElementForUpdatingParameterInfo(updateContext) ?: throw AssertionFailedError("Parameter not found")
            handler.updateParameterInfo(element, updateContext)
            TestCase.assertEquals(index, updateContext.currentParameter)
        } else if (elt != null) {
            throw AssertionFailedError("Unexpected hint found")
        }
    }
}
