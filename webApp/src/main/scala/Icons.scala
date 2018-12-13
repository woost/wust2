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

  val conversation: IconDefinition = freeRegular.faComments
  val tasks: IconDefinition = freeSolid.faTasks
  val stage: IconDefinition = freeSolid.faCodeBranch

  val task: IconDefinition = freeRegular.faCheckSquare

  val userPermission: IconDefinition = freeSolid.faUserLock
  val permissionInherit: IconDefinition = freeSolid.faArrowUp
  val permissionPrivat: IconDefinition = freeSolid.faLock
  val permissionPublic: IconDefinition = freeSolid.faGlobeAmericas

  val convertItem: IconDefinition = freeSolid.faExchangeAlt
  val mentionIn: IconDefinition = freeSolid.faCopy
  val signOut: IconDefinition =  freeSolid.faSignOutAlt
  val menu: IconDefinition = freeSolid.faCog
  val share: IconDefinition = freeSolid.faShareAlt
  val search: IconDefinition = freeSolid.faSearch
  val user: IconDefinition = freeSolid.faUsers

  val notificationsEnabled: IconDefinition = freeSolid.faBell
  val notificationsDisabled: IconDefinition = freeRegular.faBellSlash

}
