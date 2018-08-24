/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionType
import org.intellij.lang.annotations.Language

class RsSmartCompletionContributorTest : RsCompletionTestBase() {

    fun `test let`() = checkCompletion("onLet", """
        let x =/*caret*/
    """, """
        let x =/*s*//*caret*/
    """)

    fun `test let 1`() = checkCompletion("onLet", """
        let x = /*caret*/
    """, """
        let x = /*s*//*caret*/
    """)

    fun `test let 3`() = checkCompletion("onLet", """
        let x=/*caret*/
    """, """
        let x=/*s*//*caret*/
    """)

    fun `test let 4`() = checkCompletion("onLet", """
         fn main() {
             let x= /*caret*/
         }
    """, """
        fn main() {
            let x= /*s*//*caret*/
        }
    """)

    fun `test boolean`() = checkCompletion("onBoolean", """
        fn main() {
            if/*caret*/
        }
    """, """
        fn main() {
            if/*s*//*caret*/
        }
    """)

    fun `test boolean 1`() = checkCompletion("onBoolean", """
        fn main() {
            if /*caret*/
        }
    """, """
        fn main() {
            if
        } /*s*//*caret*/
    """)

    fun `test boolean 2`() = checkCompletion("onBoolean", """
        fn main() {
            if (/*caret*/)
        }
    """, """
        fn main() {
            if (/*s*//*caret*/)
        }
    """)


    private fun checkCompletion(
        lookupStrings: List<String>,
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) {
        for (lookupString in lookupStrings) {
            checkCompletion(lookupString, before, after)
        }
    }

    private fun checkCompletion(
        lookupString: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = checkByText(
        """fn main() { /*b*/ }""".replace("/*b*/", before),
        """fn main() { /*a*/ }""".replace("/*a*/", after).replace("/*s*/", lookupString)) {
        println("BEFORE: " + """fn main() { /*b*/ }""".replace("/*b*/", before))
        println("AFTER: " + """fn main() { /*a*/ }""".replace("/*a*/", after).replace("/*s*/", lookupString))
        val items = myFixture.complete(CompletionType.SMART)
            ?: return@checkByText // single completion was inserted
        val lookupItem = items.find { it.lookupString == lookupString } ?: return@checkByText
        myFixture.lookup.currentItem = lookupItem
        myFixture.type('\n')
    }
}
