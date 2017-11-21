package org.coroutines.common



import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

/**
 * Most of the methods that expect a stack pointer have the invariant
 * that, if there is a stack named
 *
 *     foo: Array[T]
 *
 * in scope, there is also a `Int` variable
 *
 *     fooptr: Int
 *
 * in scope. The stack pointer is assumed to always point at the next
 * unused slot. In consequence, all slots below the stack pointer
 * are assumed to be in use.
 *
 * In particular those variables will be sometimes re-assigned. They
 * are expected to be *named* references, not *values. That is,
 * a the call
 *
 *     copy(foo, bar)
 *
 * is valid, and the references foo, fooptr, bar and barptr are
 * expected to exist. In contrast
 *
 *     copy(new Array(), new Array())
 *
 * is invalid.
 *
 * Stacks are initialized on demand, that is: each method first
 * calls initMacro to check whether the stack needs to be created.
 * Here, `size` specifies size of the stack for initialization.
 */
object Stack {
  def init[T](stack: Array[T], size: Int): Unit = macro initMacro[T]

  def initMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree, size: c.Tree): c.Tree = {
    import c.universe._

    val tpe = implicitly[c.WeakTypeTag[T]]
    if (size == q"-1") q"" else q"""
      if ($stack == null) $stack = new _root_.scala.Array[$tpe]($size)
    """
  }

  /**
   * Copies all elements from the source array to a newly initialized array
   * which will be assigned to the reference `dest`. Also copies the stack
   * pointer to `dest`ptr.
   *
   * @param src   named reference to the source array
   * @param dest  named reference to the target array
   * @tparam T
   */
  def copy[T](src: Array[T], dest: Array[T]): Unit = macro copyMacro[T]

  def copyMacro[T: c.WeakTypeTag](c: Context)(src: c.Tree, dest: c.Tree): c.Tree = {
    import c.universe._

    val q"$srcpath.${srcname: TermName}" = src
    val srcptrname = TermName(s"${srcname}ptr")
    val srcptr = q"$srcpath.$srcptrname"
    val q"$destpath.${destname: TermName}" = dest
    val destptrname = TermName(s"${destname}ptr")
    val destptr = q"$destpath.$destptrname"
    val tpe = implicitly[WeakTypeTag[T]]

    q"""
      $destptr = $srcptr
      if ($src != null) {
        $dest = new _root_.scala.Array[$tpe]($src.length)
        _root_.java.lang.System.arraycopy($src, 0, $dest, 0, $srcptr)
      }
    """
  }

  /**
   * Pushes a given element x to the stack, potentially resizing to fit.
   *
   * @param stack
   * @param x      the element to be pushed
   * @param size   the initial size of the stack, if not yet initialized
   * @tparam T     the type of the elements on the stack
   */
  def push[T](stack: Array[T], x: T, size: Int): Unit = macro pushMacro[T]

  def pushMacro[T: c.WeakTypeTag](c: Context)(
    stack: c.Tree, x: c.Tree, size: c.Tree
  ): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val tpe = implicitly[WeakTypeTag[T]]
    q"""
      _root_.org.coroutines.common.Stack.init[$tpe]($stack, $size)
      if ($stackptr >= $stack.length) {
        val nstack = new _root_.scala.Array[$tpe]($stack.length * 2)
        _root_.java.lang.System.arraycopy($stack, 0, nstack, 0, $stack.length)
        $stack = nstack
      }
      $stack($stackptr) = $x
      $stackptr += 1
    """
  }

  /**
   * Grows a given stack to fit `n` more elements. Starts with `size`
   * if the stack is not yet initialized. Does not actually "push" elements
   * on the stack.
   *
   * TODO think about renaming this to grow
   *
   * @param stack
   *
   * @param n       the number of additional elements the stack should be
   *                resized to fit.
   *
   * @param size    the initial size of the stack, if not yet initialized
   *
   * @tparam T      the type of the elements on the stack
   */
  def bulkPush[T](stack: Array[T], n: Int, size: Int): Unit = macro bulkPushMacro[T]

  def bulkPushMacro[T: c.WeakTypeTag](c: Context)(
    stack: c.Tree, n: c.Tree, size: c.Tree
  ): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val tpe = implicitly[WeakTypeTag[T]]
    q"""
      _root_.org.coroutines.common.Stack.init[$tpe]($stack, $size)
      $stackptr += $n
      while ($stackptr >= $stack.length) {
        val nstack = new _root_.scala.Array[$tpe]($stack.length * 2)
        _root_.java.lang.System.arraycopy($stack, 0, nstack, 0, $stack.length)
        $stack = nstack
      }
    """
  }

  /**
   * Removes one element from the stack, overrides it with null and returns it
   */
  def pop[T](stack: Array[T]): T = macro popMacro[T]

  def popMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val tpe = implicitly[WeakTypeTag[T]]
    val valnme = TermName(c.freshName())
    q"""
      $stackptr -= 1
      val $valnme = $stack($stackptr)
      $stack($stackptr) = null.asInstanceOf[$tpe]
      $valnme
    """
  }

  /**
   * Removes n elements from the stack by just changing the stackpointer
   */
  def bulkPop[T](stack: Array[T], n: Int): Unit = macro bulkPopMacro[T]

  def bulkPopMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree, n: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val tpe = implicitly[WeakTypeTag[T]]
    val valnme = TermName(c.freshName())
    q"""
      $stackptr -= $n
    """
  }

  /**
   * Retreives the top most element on the stack
   */
  def top[T](stack: Array[T]): T = macro topMacro[T]

  def topMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    q"""
      $stack($stackptr - 1)
    """
  }

  /**
   * Get the element at position n, counting down from the top of the stack
   */
  def get[T](stack: Array[T], n: Int): T = macro getMacro[T]

  def getMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree, n: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val valnme = TermName(c.freshName())
    q"""
      $stack($stackptr - 1 - $n)
    """
  }

  /**
   * Stores an element `x` at position `stackptr - 1 - n` on the stack
   */
  def set[T](stack: Array[T], n: Int, x: T): Unit = macro setMacro[T]

  def setMacro[T: c.WeakTypeTag](c: Context)(
    stack: c.Tree, n: c.Tree, x: c.Tree
  ): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val valnme = TermName(c.freshName())
    q"""
      $stack($stackptr - 1 - $n) = $x
    """
  }


  /**
   * Updates the top-most element of the stack by replacing it with `x`
   */
  def update[T](stack: Array[T], x: T): T = macro updateMacro[T]

  def updateMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree, x: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val valnme = TermName(c.freshName())
    q"""
      val $valnme = $stack($stackptr - 1)
      $stack($stackptr - 1) = $x
      $valnme
    """
  }

  /**
   * Determines whether the stack is empty by comparing with the stackpointer
   */
  def isEmpty[T](stack: Array[T]): Boolean = macro isEmptyMacro[T]

  def isEmptyMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    q"""
      $stackptr <= 0
    """
  }

}
