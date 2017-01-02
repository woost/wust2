package frontend

import org.scalajs.dom._

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

abstract class CustomComponent[_Props](componentName: String = "CustomComponent") {
  type Props = _Props
  type Scope = BackendScope[Props, Unit]

  abstract class CustomBackend($: Scope) {
    def render(p: Props) = <.div(^.ref := "component")
    lazy val component = Ref[raw.HTMLElement]("component")($).get

    def init() {}
    def update(p: Props, oldProps: Option[Props] = None) {}
    def cleanup() {}
  }
  val backendFactory: Scope => CustomBackend

  protected val component = ReactComponentB[Props](componentName)
    .backend(backendFactory(_))
    .render(c => c.backend.render(c.props))
    .componentDidMount(c => Callback { c.backend.init(); c.backend.update(c.props, None) })
    .componentWillReceiveProps(c => Callback { c.$.backend.update(c.nextProps, Some(c.currentProps)) })
    .shouldComponentUpdate(_ => false) // let our custom code handle the update instead
    .componentWillUnmount(c => Callback { c.backend.cleanup() })
    .build

  def apply(p: Props) = component(p)
}
