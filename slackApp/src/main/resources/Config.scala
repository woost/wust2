package woost.slack

import autoconfig.config

@config(section = "wust.slack")
object Config {
  val accessToken: String
  val wustHost: String
}
