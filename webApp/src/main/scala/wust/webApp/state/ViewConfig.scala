package wust.webApp.state

import acyclic.file
import wust.graph._
import wust.ids.View

// ViewConfig and UrlConfig are the configurations driving our application ui.
// For example, it contains the page and view that should be displayed.
// The UrlConfig is the raw configuration derived from the url.
// The ViewConfig is the sanitized configuration for the views. For example, the
// page in viewconfig is always consistent with the graph, i.e., it is contained in the graph
// or else it will be none.

//TODO: get rid of pagechange, currently needed to know whether we should get a new graph on page change or not.
// we only know whether we need this when changing the page. But it feels like mixing data and commands.

case class ShareOptions(title: String, text: String, url: String)
case class PageChange(page: Page, needsGet: Boolean = true)

case class ViewConfig(view: View.Visible, page: Page)
