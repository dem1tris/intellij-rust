/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring

import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

class RsInlineVariableHandlerTest : RsTestBase() {

    fun `test simple`() = doTest("""
        fn main() {
            let a = 5;
            let b = a/*caret*/;
        }
    """, """
        fn main() {
            let b = 5/*caret*/;
        }
    """)

    fun `test decl`() = doTest("""
        fn main() {
            let a/*caret*/ = 5;
            let b = a;
        }
    """, """
        fn main() {
            let b = 5/*caret*/;
        }
    """)

    fun `test need parentheses`() = doTest("""
        fn main() {
            let a = 5 + 5;
            let b = a/*caret*/;
        }
    """, """
        fn main() {
            let b = (5 + 5)/*caret*/;
        }
    """)

    fun `test fn`() = doTest("""
        fn bar() -> i32 { 42 }

        fn main() {
            let x = bar();
            x/*caret*/ + 12 - x * 23
        }
    """, """
        fn bar() -> i32 { 42 }

        fn main() {
            bar()/*caret*/ + 12 - bar() * 23
        }
    """)


    private fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) {
        checkByText(before, after) {
            myFixture.performEditorAction("Inline")
        }
    }
}
