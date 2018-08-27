/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionType
import org.intellij.lang.annotations.Language

class RsSmartCompletionContributorTest : RsCompletionTestBase() {

    fun `test let`() = checkCompletion("onLet", """
        fn main() {
            let x =/*caret*/
        }
    """, """
        fn main() {
            let x =/*s*//*caret*/
        }
    """)

    fun `test let 1`() = checkCompletion("onLet", """
        fn main() {
            let x = /*caret*/
        }
    """, """
        fn main() {
            let x = /*s*//*caret*/
        }
    """)

    fun `test let 3`() = checkCompletion("onLet", """
        fn main() {
            let x=/*caret*/
        }
    """, """
        fn main() {
            let x=/*s*//*caret*/
        }
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

    fun `test while`() = checkCompletion("onBoolean", """
        fn main() {
            while /*caret*/
        }
    """, """
        fn main() {
            while /*s*//*caret*/
        }
    """)

    fun `test if`() = checkCompletion("onBoolean", """
        fn main() {
            if /*caret*/
        }
    """, """
        fn main() {
            if /*s*//*caret*/
        }
    """)

    fun `test if paren`() = checkCompletion("onBoolean", """
        fn main() {
            if (/*caret*/)
        }
    """, """
        fn main() {
            if (/*s*//*caret*/)
        }
    """)

    fun `test value arglist`() = checkCompletion("onVAL", """
        fn foo(x: i32) -> i32 {
            x
        }
        fn main() {
            foo(/*caret*/)
        }
    """, """
        fn foo(x: i32) -> i32 {
            x
        }
        fn main() {
            foo(/*s*//*caret*/)
        }
    """)

    fun `test value arglist a`() = checkCompletion("onVAL", """
        fn foo(x: i32) -> i32 {
            x
        }
        fn main() {
            foo(a/*caret*/)
        }
    """, """
        fn foo(x: i32) -> i32 {
            x
        }
        fn main() {
            foo(/*s*//*caret*/)
        }
    """)

    fun `test nested if`() = checkCompletion("onBoolean", """
        fn foo(x: i32) -> i32 {
            x
        }
        fn main() {
            let x = 5;
            let y = 7;
            foo(if /*caret*/);
        }
    """, """
        fn foo(x: i32) -> i32 {
            x
        }
        fn main() {
            let x = 5;
            let y = 7;
            foo(if /*s*//*caret*/);
        }
    """)

    fun `test nested if 1`() = checkCompletion("onBoolean", """
        fn foo(x: i32) -> i32 {
            x
        }
        fn main() {
            let x = 5;
            let y = 7;
            foo(if /*caret*/{ 5 } else { 7 });
        }
    """, """
        fn foo(x: i32) -> i32 {
            x
        }
        fn main() {
            let x = 5;
            let y = 7;
            foo(if /*s*//*caret*/{ 5 } else { 7 });
        }
    """)

    fun `test returnable`() = checkCompletion("onReturnable", """
        struct S;
        fn foo(x: i32) -> S {
            /*caret*/
        }
    """, """
        struct S;
        fn foo(x: i32) -> S {
            /*s*//*caret*/
        }
    """)

    fun `test returnable comment`() = checkCompletion("onReturnable", """
        struct S;
        fn foo(x: i32) -> S {
            /*caret*/// comment
        }
    """, """
        struct S;
        fn foo(x: i32) -> S {
            /*s*//*caret*/// comment
        }
    """)

    fun `test retexpr`() = checkCompletion("onReturnable", """
        struct S;
        fn foo(x: i32) -> S {
            if 5 == 5 {
                return /*caret*/// comment
                let y = 5 + 5;
                return S;
            } else {
                return S;
            }
        }
    """, """
        struct S;
        fn foo(x: i32) -> S {
            if 5 == 5 {
                return /*s*//*caret*/// comment
                let y = 5 + 5;
                return S;
            } else {
                return S;
            }
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
        before, after.replace("/*s*/", lookupString)) {
        val items = myFixture.complete(CompletionType.SMART)
            ?: return@checkByText // single completion was inserted
        val lookupItem = items.find { it.lookupString == lookupString } ?: return@checkByText
        myFixture.lookup.currentItem = lookupItem
        myFixture.type('\n')
    }
}
