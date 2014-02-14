package com.socrata.pg.server.config

import com.typesafe.config.Config
import com.socrata.pg.config.{StoreConfig}
import com.socrata.thirdparty.typesafeconfig.ConfigClass

class QueryServerConfig(val config: Config, val root: String) extends ConfigClass(config, root) {
  val log4j = getRawConfig("log4j")
  val store = new StoreConfig(config, path("store"))
  val port = getInt("port")
  val curator = new CuratorConfig(config, path("curator"))
  val advertisement = new AdvertisementConfig(config, path("service-advertisement"))
  val instance = getString("instance")
}
