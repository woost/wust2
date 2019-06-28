package wust.facades.tribute

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation._

@js.native
@JSImport("tributejs", JSImport.Default)
class Tribute[Value](collection: TributeCollection[Value]) extends js.Object {
  def attach(element: dom.html.Element): Unit = js.native
  def detach(element: dom.html.Element): Unit = js.native
  def showMenuForCollection(element: dom.html.Element, collectionIndex: Int = ???): Unit = js.native
  def isActive: Boolean = js.native
  def append(collectionIndex: Int, values: js.Array[Value]): Unit = js.native
  def appendCurrent(values: js.Array[Value]): Unit = js.native
}
object Tribute {
  import outwatch.AsVDomModifier
  import outwatch.dom._
  import outwatch.dom.helpers.EmitterBuilder
  import monix.execution.Cancelable

  implicit def render[Value]: AsVDomModifier[Tribute[Value]] = { tribute =>
    VDomModifier(
      snabbdom.VNodeProxy.repairDomBeforePatch, // the emoji-picker modifies the dom
      managedElement.asHtml { element =>
        tribute.attach(element)
        Cancelable { () =>
          tribute.detach(element)
        }
      }
    )
  }

  def replacedEvent = EmitterBuilder[dom.Event]("tribute-replaced")
  def noMatchEvent = EmitterBuilder[dom.Event]("tribute-no-match")
}

trait TributeCollection[Value] extends js.Object {
  // symbol that starts the lookup
  var trigger: js.UndefOr[String] = js.undefined
  // element to target for @mentions
  var iframe: js.UndefOr[dom.html.Element] = js.undefined
  // class added in the flyout menu for active item
  var selectClass: js.UndefOr[String] = js.undefined
  // function called on select that returns the content to insert
  var selectTemplate: js.UndefOr[js.Function1[TributeItem[Value], String]] = js.undefined
  // template for displaying item in menu
  var menuItemTemplate: js.UndefOr[js.Function1[TributeItem[Value], String]] = js.undefined
  // template for when no match is found (optional),
  // If no template is provided, menu is hidden.
  var noMatchTemplate: js.UndefOr[String] = js.undefined
  // specify an alternative parent container for the menu
  var menuContainer: js.UndefOr[dom.html.Element] = js.undefined
  // column to search against in the object (accepts function or string)
  var lookup: js.UndefOr[String | js.Function2[Value, String, String]] = js.undefined
  // column that contains the content to insert by default
  var fillAttr: js.UndefOr[String] = js.undefined
  // REQUIRED: array of objects to match
  var values: js.UndefOr[js.Array[Value] | js.Function2[String, js.Function1[js.Array[Value], Unit], Unit]] = js.undefined
  // specify whether a space is required before the trigger character
  var requireLeadingSpace: js.UndefOr[Boolean] = js.undefined
  // specify whether a space is allowed in the middle of mentions
  var allowSpaces: js.UndefOr[Boolean] = js.undefined
  // optionally specify a custom suffix for the replace text
  // (defaults to empty space if undefined)
  var replaceTextSuffix: js.UndefOr[String] = js.undefined
  // specify whether the menu should be positioned.  Set to false and use in conjuction with menuContainer to create an inline menu
  // (defaults to true)
  var positionMenu: js.UndefOr[Boolean] = js.undefined
  // when the spacebar is hit, select the current match
  var spaceSelectsMatch: js.UndefOr[Boolean] = js.undefined
  // turn tribute into an autocomplete
  var autocompleteMode: js.UndefOr[Boolean] = js.undefined
  // Customize the elements used to wrap matched strings within the results list
  // defaults to <span></span> if undefined
  var searchOpts: js.UndefOr[TributeSearchOpts] = js.undefined
}

trait TributeSearchOpts extends js.Object {
  var pre: js.UndefOr[String] = js.undefined
  var post: js.UndefOr[String] = js.undefined
}

@js.native
trait TributeItem[Value] extends js.Object {
  def index: Int = js.native
  def original: Value = js.native
  def score: Int = js.native
  def string: String = js.native
}
