import sbt._

object Dependencies {
  object versions {
    val c3p0 = "0.9.5-pre9"
    val commonsCli = "1.2"
    val commonsCodec = "1.5"
    val commonsIo = "1.4"
    val commonsLang = "2.6"
    val jodaConvert = "1.2"
    val jodaTime = "2.1"
    val liquibaseCore = "2.0.0"
    val liquibasePlugin = "1.9.5.0"
    val postgresql = "9.3-1102-jdbc41"
    val scalaTest = "2.1.0-RC2"
    val scalaCheck = "1.12.4"
    val simpleArm = "1.1.10"
    val slf4j = "1.7.5"
    val socrataUtils = "0.8.0"
    val socrataThirdPartyUtil = "3.1.2"
    val socrataHttpCuratorBroker = "3.3.0"
    val soqlStdlib = "0.5.4"
    val typesafeConfig = "1.0.0"
    val dataCoordinator = "0.7.6"
    val typesafeScalaLogging = "1.1.0"
    val rojomaJson = "3.2.0"
    val metricsJetty = "3.1.0"
    val metricsGraphite = "3.0.2"
    val metricsScala = "3.3.0"
    val googleApiClient = "1.20.0"
    val googleHttpClient = "1.20.0"
    val googleBigqueryApi = "v2-rev217-1.20.0"
  }

  val c3p0 = "com.mchange" % "c3p0" % versions.c3p0

  val commonsCli = "commons-cli" % "commons-cli" % versions.commonsCli

  val commonsCodec = "commons-codec" % "commons-codec" % versions.commonsCodec

  val commonsIo = "commons-io" % "commons-io" % versions.commonsIo

  val commonsLang = "commons-lang" % "commons-lang" % versions.commonsLang

  val googleApiClient = "com.google.api-client" % "google-api-client" % versions.googleApiClient excludeAll(
      ExclusionRule(organization = "com.google.guava"),
      ExclusionRule(organization = "commons-logging")
    )

  val googleBigqueryApi = "com.google.apis" % "google-api-services-bigquery" % versions.googleBigqueryApi

  val googleHttpClient = "com.google.http-client" % "google-http-client-jackson2" % versions.googleHttpClient exclude("commons-logging", "commons-logging")

  val jodaConvert = "org.joda" % "joda-convert" % versions.jodaConvert

  val jodaTime = "joda-time" % "joda-time" % versions.jodaTime

  val liquibaseCore = "org.liquibase" % "liquibase-core" % versions.liquibaseCore

  val liquibasePlugin = "org.liquibase" % "liquibase-plugin" % versions.liquibasePlugin

  val postgresql = "org.postgresql" % "postgresql" % versions.postgresql

  val scalaTest = "org.scalatest" %% "scalatest" % versions.scalaTest

  val scalaCheck = "org.scalacheck" %% "scalacheck" % versions.scalaCheck

  val simpleArm = "com.rojoma" %% "simple-arm" % versions.simpleArm

  val socrataUtil = "com.socrata" %% "socrata-utils" % versions.socrataUtils

  val socrataThirdPartyUtil = "com.socrata" %% "socrata-thirdparty-utils" % versions.socrataThirdPartyUtil

  val socrataHttpCuratorBroker = "com.socrata" %% "socrata-http-curator-broker" % versions.socrataHttpCuratorBroker

  val soqlStdlib = "com.socrata" %% "soql-stdlib" % versions.soqlStdlib

  val typesafeConfig = "com.typesafe" % "config" % versions.typesafeConfig

  val secondarylib = "com.socrata" %% "secondarylib" % versions.dataCoordinator // % "provided"
  val coordinatorlib = "com.socrata" %% "coordinator" % versions.dataCoordinator

  val slf4j = "org.slf4j" % "slf4j-log4j12" % versions.slf4j

  val typesafeScalaLogging = "com.typesafe" %% "scalalogging-slf4j" % versions.typesafeScalaLogging

  val rojomaJson = "com.rojoma" %% "rojoma-json-v3" % versions.rojomaJson

  val metricsJetty = "io.dropwizard.metrics" % "metrics-jetty9" % versions.metricsJetty
  // See CORE-3635: use lower version of graphite to work around Graphite reconnect issues
  val metricsGraphite = "com.codahale.metrics" % "metrics-graphite" % versions.metricsGraphite exclude(
                           "com.codahale.metrics", "metrics-core")
  val metricsScala = "nl.grons" %% "metrics-scala" % versions.metricsScala
}
