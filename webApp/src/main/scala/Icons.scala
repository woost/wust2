package wust.webApp

import fontAwesome._
import outwatchHelpers._

object Icons {
  val zoom: IconDefinition = freeSolid.faSearchPlus
  val edit: IconDefinition = freeRegular.faEdit
  val delete: IconDefinition = freeRegular.faTrashAlt
  val undelete: Layer = fontawesome.layered(
    fontawesome.icon(freeRegular.faTrashAlt),
    fontawesome.icon(freeSolid.faMinus, new Params {
      transform = new Transform {
        rotate = 45.0
      }

    })
  )
  val task: IconDefinition = freeRegular.faCheckSquare

  val conversation: IconDefinition = freeRegular.faComments
  val tasks: IconDefinition = freeSolid.faTasks
}
