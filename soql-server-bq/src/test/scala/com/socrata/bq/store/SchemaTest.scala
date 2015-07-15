package com.socrata.bq.store

import scala.io.Source
import scala.language.reflectiveCalls

import com.rojoma.json.v3.util.JsonUtil
import com.socrata.datacoordinator.common.{DataSourceConfig, DataSourceFromConfig}
import com.socrata.bq.Schema
import com.socrata.bq.server.{PGQueryServerDatabaseTestBase, QueryServerTest}
import com.socrata.bq.query.PGQueryTestBase

class SchemaTest extends PGSecondaryTestBase with PGQueryServerDatabaseTestBase with PGQueryTestBase {
  import com.socrata.bq.store.PGSecondaryUtil._
  import Schema._

  override def beforeAll() {
    createDatabases()
  }

  test("schema json codec") {
    val dsConfig = new DataSourceConfig(config, "database")
    val ds = DataSourceFromConfig(dsConfig)
    for (dsInfo <- ds) {
      withPgu() { pgu =>
      val f = columnsCreatedFixture
      f.pgs._version(pgu, f.datasetInfo, f.dataVersion+1, None, f.events.iterator)
      val qs = new QueryServerTest(dsInfo, pgu)
      val schema = qs.getSchema(testInternalName, None).get
      val schemaj = JsonUtil.renderJson(schema)
      val schemaRoundTrip = JsonUtil.parseJson[com.socrata.datacoordinator.truth.metadata.Schema](schemaj)
        .right.toOption.get
      schema should be (schemaRoundTrip)
      val expected = Source.fromURL(getClass.getResource("/fixtures/schema.json"))
      val expectedSchema = JsonUtil.readJson[com.socrata.datacoordinator.truth.metadata.Schema](expected.reader())
        .right.toOption.get
      schema should be (expectedSchema)
      }
    }
  }
}