package wust.android

import android.app.Activity
import android.os.Bundle
import android.widget.{Button, LinearLayout, TextView}
import macroid._
import macroid.contrib._
import macroid.FullDsl._

object OurTweaks {
  def greeting(greeting: String)(implicit ctx: ContextWrapper) =
    TextTweaks.large +
    text(greeting) +
    hide

  def orient(implicit ctx: ContextWrapper) =
    landscape ? horizontal | vertical
}

class MainActivity extends Activity with Contexts[Activity] {
  var greeting = slot[TextView]

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    setContentView {
      Ui.get {
        l[LinearLayout](
          w[Button] <~
            text("Click me") <~
            On.click {
              greeting <~ show
            },
          w[Button] <~
            text("Click me 2") <~
            On.click {
              greeting <~ hide
            },
          w[TextView] <~
            wire(greeting) <~
            OurTweaks.greeting("Hello!")
        ) <~ OurTweaks.orient
      }
    }
  }
}