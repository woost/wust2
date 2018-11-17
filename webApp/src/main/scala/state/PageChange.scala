package wust.webApp.state

import wust.graph.Page

case class PageChange(page: Page, needsGet: Boolean = true)
