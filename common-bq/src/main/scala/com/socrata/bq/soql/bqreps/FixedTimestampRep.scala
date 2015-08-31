package com.socrata.bq.soql.bqreps

import com.rojoma.json.v3.ast.{JObject, JString, JNull, JValue}
import com.socrata.bq.soql.BigQueryRep
import com.socrata.soql.types._
import org.joda.time.{DateTimeZone, DateTime}

class FixedTimestampRep extends BigQueryRep[SoQLType, SoQLValue] {

  override def repType: SoQLType = SoQLFixedTimestamp

  override val bigqueryType: String = "TIMESTAMP"

  override def SoQL(row: Seq[String]): SoQLValue = {
    if (row.head == null)
      SoQLNull
    else
      // Timestamp strings can be returned from BQ as either plain numbers or scientific notation,
      // so we need the additional toDouble conversion to avoid NumberFormatExceptions
      SoQLFixedTimestamp(new DateTime(row.head.toDouble.toLong, DateTimeZone.UTC))
  }

  override def jvalue(value: SoQLValue): JValue = {
    if (value == SoQLNull) JNull
    else JString(SoQLFixedTimestamp.StringRep(value.asInstanceOf[SoQLFixedTimestamp].value))
  }

  override def numColumns: Int = 1
}
