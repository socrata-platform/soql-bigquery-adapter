package com.socrata.bq.server

import com.socrata.bq.query.TotalRowCount

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class SoQLGroupByTest extends QueryTest {

  test("simple group by") {
    val expected = ArrayBuffer[mutable.Buffer[String]](mutable.Buffer("51", "false"), mutable.Buffer("49", "true"))
    queryAndCompare(s"SELECT COUNT(*), boolean FROM ${QueryTest.FULL_TABLE_NAME} GROUP BY boolean", Option(expected), 2)
  }

}