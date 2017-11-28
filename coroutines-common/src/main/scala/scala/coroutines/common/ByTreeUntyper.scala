package org.coroutines.common



import scala.collection._
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

// TODO this treeValue is of type any since Scala 2.11 doesn't support path dependent
//      types on previous argument sections of class constructors
private[coroutines] class ByTreeUntyper[C <: Context](val c: C)(treeValue: Any) {
  import c.universe._

  private val tree = treeValue.asInstanceOf[Tree]
  private val untypedTree = c.untypecheck(tree)
  private val treeMapping = mutable.Map[Tree, Tree]()
  private val traverser = new TraverserUtil[c.type](c)

  // traverse once to fill the above maps with information
  traverser.traverseByShape(tree, untypedTree) { (t, pt) => treeMapping(t) = pt }

  // TODO why is t not untypechecked when not included in the map / part of the lambda?
  def untypecheck(t: Tree) = treeMapping.getOrElse(t, t)
}
private[coroutines] object ByTreeUntyper {
  // we loose type information here by calling the constructor. This is restored in the above cast
  def apply(c: Context)(value: c.Tree): ByTreeUntyper[c.type] = new ByTreeUntyper[c.type](c)(value)
}
