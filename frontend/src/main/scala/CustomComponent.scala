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

    def init(p: Props) = Callback.empty
    def update(p: Props, oldProps: Option[Props] = None) = Callback.empty
    def cleanup() = Callback.empty
  }
  val backendFactory: Scope => CustomBackend

  protected val component = ReactComponentB[Props](componentName)
    .backend(backendFactory(_))
    .render(c => c.backend.render(c.props))
    .componentDidMount(c => c.backend.init(c.props) >> c.backend.update(c.props, None))
    .componentWillReceiveProps(c => c.$.backend.update(c.nextProps, Some(c.currentProps)))
    .shouldComponentUpdate(_ => false) // let our custom code handle the update instead
    .componentWillUnmount(c => c.backend.cleanup())
    .build

  def apply(p: Props) = component(p)
}
