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

  val filterDropdown: Layer = fontawesome.layered(
    fontawesome.icon(freeSolid.faFilter),
    fontawesome.icon(freeSolid.faCaretDown, new Params {
      transform = new Transform {
        x = 5.0
        y = 10.0
        size = 10.0
      }

    })
  )
  val filter: IconDefinition = freeSolid.faFilter
  val noFilter: Layer = fontawesome.layered(
    fontawesome.icon(freeSolid.faFilter),
    fontawesome.icon(freeSolid.faMinus, new Params {
      transform = new Transform {
        rotate = 45.0
      }

    })
  )
  val conversation: IconDefinition = freeRegular.faComments
  val tasks: IconDefinition = freeSolid.faTasks
  val files: IconDefinition = freeSolid.faPaperclip
  val stage: IconDefinition = freeSolid.faColumns

  val task: IconDefinition = freeRegular.faCheckSquare

  val fileUpload: IconDefinition = freeSolid.faFileUpload
  val userPermission: IconDefinition = freeSolid.faUserLock
  val permissionInherit: IconDefinition = freeSolid.faArrowUp
  val permissionPrivate: IconDefinition = freeSolid.faLock
  val permissionPublic: IconDefinition = freeSolid.faGlobeAmericas

  val convertItem: IconDefinition = freeSolid.faExchangeAlt
  val mentionIn: IconDefinition = freeSolid.faCopy
  val signOut: IconDefinition =  freeSolid.faSignOutAlt
  val menu: IconDefinition = freeSolid.faCog
  val menuDropdown: Layer = fontawesome.layered(
    fontawesome.icon(freeSolid.faCog),
    fontawesome.icon(freeSolid.faCaretDown, new Params {
      transform = new Transform {
        x = 5.0
        y = 10.0
        size = 10.0
      }

    })
  )
  val share: IconDefinition = freeSolid.faShareAlt
  val search: IconDefinition = freeSolid.faSearch
  val user: IconDefinition = freeSolid.faUsers

  val notificationsEnabled: IconDefinition = freeSolid.faBell
  val notificationsDisabled: IconDefinition = freeRegular.faBellSlash

  val expand: IconDefinition = freeRegular.faPlusSquare
  val collapse: IconDefinition = freeRegular.faMinusSquare
}
