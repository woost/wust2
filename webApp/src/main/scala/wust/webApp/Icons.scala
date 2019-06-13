package wust.webApp

import fontAwesome._

object Icons {

  val help: IconDefinition = freeSolid.faQuestion
  val ellipsisV: IconDefinition = freeSolid.faEllipsisV
  val sync: IconDefinition = freeSolid.faExchangeAlt
  val plugin: IconDefinition = freeSolid.faPuzzlePiece
  val slack: IconDefinition  = freeBrands.faSlack

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
  val dashboard: IconDefinition = freeSolid.faChartLine
  val project: IconDefinition = freeSolid.faProjectDiagram
  val note: IconDefinition = freeSolid.faStickyNote

  val conversation: IconDefinition = freeRegular.faComments
  val chat: IconDefinition = freeRegular.faComments
  val thread = fontawesome.layered(
    fontawesome.icon(freeRegular.faComments, new Params {
      transform = new Transform {
        size = 14.0
        x = -2.0
        y = 2.0
      }
    }),
    fontawesome.icon(freeSolid.faCodeBranch, new Params {
      transform = new Transform {
        flipY = true
        x = 6.0
        y = -6.0
        size = 8.0
      }
    })
  )
  val table: IconDefinition = freeSolid.faTable
  val tasks: IconDefinition = freeSolid.faTasks
  val kanban: IconDefinition = freeSolid.faColumns
  val list: IconDefinition = freeSolid.faList
  val files: IconDefinition = freeSolid.faPaperclip
  val stage: IconDefinition = freeSolid.faColumns
  val tags: IconDefinition = freeSolid.faTags
  val tag: IconDefinition = freeSolid.faTag
  val graph: IconDefinition = freeBrands.faCloudsmith
  val notes: IconDefinition = freeRegular.faStickyNote
  val gantt: IconDefinition = freeSolid.faStream
  val topological: IconDefinition = freeSolid.faSortNumericDown
  val notifications: IconDefinition = freeRegular.faBell

  val task: IconDefinition = freeRegular.faCheckSquare

  val fileUpload: IconDefinition = freeSolid.faPaperclip
  val userPermission: IconDefinition = freeSolid.faUserLock
  val permissionInherit: IconDefinition = freeSolid.faArrowUp
  val permissionPrivate: IconDefinition = freeSolid.faLock
  val permissionPublic: IconDefinition = freeSolid.faGlobeAmericas

  val automate: IconDefinition =  freeSolid.faRobot
  val `import`: IconDefinition =  freeSolid.faFileImport
  val convertItem: IconDefinition = freeSolid.faExchangeAlt
  val mentionIn: IconDefinition = freeSolid.faCopy
  val pin: IconDefinition = freeSolid.faThumbtack
  val signOut: IconDefinition =  freeSolid.faSignOutAlt
  val menu: IconDefinition = freeSolid.faCog

  val share: IconDefinition = freeSolid.faShareAlt
  val search: IconDefinition = freeSolid.faSearch
  val users: IconDefinition = freeSolid.faUsers

  val notificationsEnabled: IconDefinition = freeSolid.faBell
  val notificationsDisabled: IconDefinition = freeRegular.faBellSlash

  val expand: IconDefinition = freeRegular.faPlusSquare
  val collapse: IconDefinition = freeRegular.faMinusSquare

  val property: IconDefinition = freeSolid.faReceipt
  val propertyZoom: Layer = fontawesome.layered(
    fontawesome.icon(freeSolid.faSearch, new Params {
      styles = scalajs.js.Dictionary[String]("color" -> "grey")
      transform = new Transform {
        x = 5.0
        y = 5.0
        size = 50.0
      }
    }),
    fontawesome.icon(freeSolid.faReceipt),
  )

  val propertyInt: Layer = fontawesome.layered( fontawesome.icon(freeSolid.faFont), fontawesome.text("123", new Params { transform = new Transform { size = 10.0 } }) )
  val propertyDec: Layer = fontawesome.layered( fontawesome.icon(freeSolid.faFont), fontawesome.text("4.2", new Params { transform = new Transform { size = 10.0 } }) )
  val propertyNumber: IconDefinition = freeSolid.faCalculator
  val propertyText: IconDefinition = freeSolid.faFont
  val propertyDate: IconDefinition = freeSolid.faCalendarDay
  val propertyCheckbox: IconDefinition = freeRegular.faCheckSquare

  val deadline: IconDefinition = freeSolid.faClock
  val reminder: Layer = fontawesome.layered(
    fontawesome.icon(freeSolid.faBell, new Params {
      transform = new Transform {
        x = 5.0
        y = -5.0
        size = 10.0
      }
    }),
    fontawesome.icon(freeSolid.faClock, new Params {
      transform = new Transform {
        x = -3.0
        y = 3.0
        size = 12.5
      }
    }),
  )
}