package wust.webApp

import fontAwesome._
import outwatchHelpers._

object Icons {
  val zoom = freeSolid.faSearchPlus
  val edit = freeRegular.faEdit
  val delete = freeRegular.faTrashAlt
  val undelete = fontawesome.layered(
    fontawesome.icon(freeRegular.faTrashAlt),
    fontawesome.icon(freeSolid.faMinus, new Params {
      transform = new Transform {
        rotate = 45.0
      }

    })
  )
}
