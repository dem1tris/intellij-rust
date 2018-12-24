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
            /*caret*/let b = 5;
        }
    """)

    //TODO: move caret out of parentheses
    fun `test need parentheses`() = doTest("""
        fn main() {
            let a = 5 + 5;
            let b = a/*caret*/;
        }
    """, """
        fn main() {
            let b = (5 + 5/*caret*/);
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

    fun `test tup 1`() = doTest("""
        fn main() {
            let (x, ) = ("123",);
            println!(x/*caret*/);
        }
    """, """
        fn main() {
            println!("123"/*caret*/);
        }
    """)

    fun `test tup 2`() = doTest("""
        fn main() {
            let (x, y) = ("123", 1);
            println!(x/*caret*/);
        }
    """, """
        fn main() {
            let y = 1;
            println!("123"/*caret*/);
        }
    """)

    fun `test tup many left`() = doTest("""
        fn main() {
            let (x, y, z) = ("123", 1, 2);
            println!(x/*caret*/);
        }
    """, """
        fn main() {
            let (y, z) = (1, 2);
            println!("123"/*caret*/);
        }
    """)

    fun `test tup many right`() = doTest("""
        fn main() {
            let (x, y, z) = ("123", 1, "123");
            println!(z/*caret*/);
        }
    """, """
        fn main() {
            let (x, y) = ("123", 1);
            println!("123"/*caret*/);
        }
    """)

    fun `test tup many center`() = doTest("""
        fn main() {
            let (x, y, z) = (1, "123", 2);
            println!(y/*caret*/);
        }
    """, """
        fn main() {
            let (x, z) = (1, 2);
            println!("123"/*caret*/);
        }
    """)

    fun `test tup left dotdot`() = doTest("""
        fn main() {
            let (x, .., z) = ("123", 1, 2);
            println!(x/*caret*/);
        }
    """, """
        fn main() {
            let (.., z) = (1, 2);
            println!("123"/*caret*/);
        }
    """)

    fun `test tup right dotdot`() = doTest("""
        fn main() {
            let (x, .., z) = ("123", 1, "123");
            println!(z/*caret*/);
        }
    """, """
        fn main() {
            let (x, ..) = ("123", 1);
            println!("123"/*caret*/);
        }
    """)

    fun `test tup dotdot save paren 1`() = doTest("""
        fn main() {
            let (.., z) = (1, "123");
            println!(z/*caret*/);
        }
    """, """
        fn main() {
            let (..) = (1,);
            println!("123"/*caret*/);
        }
    """)

    fun `test tup dotdot save paren 2`() = doTest("""
        fn main() {
            let (z, ..) = ("123", 1);
            println!(z/*caret*/);
        }
    """, """
        fn main() {
            let (..) = (1,);
            println!("123"/*caret*/);
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
