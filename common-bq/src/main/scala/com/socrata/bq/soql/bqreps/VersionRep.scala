package com.socrata.bq.soql.bqreps

import com.rojoma.json.v3.ast.{JString, JNull, JValue}
import com.socrata.bq.soql.{BigQueryWriteRep, BigQueryReadRep}
import com.socrata.soql.types.{SoQLVersion, SoQLType, SoQLValue}

class VersionRep extends BigQueryReadRep[SoQLType, SoQLValue] with BigQueryWriteRep[SoQLType, SoQLValue] {

  override def repType: SoQLType = SoQLVersion

  override val bigqueryType: String = "INTEGER"

  override def SoQL(value: String): SoQLValue = {
    // should never be null
    SoQLVersion(value.toLong)
  }

  override def jvalue(value: SoQLValue): JValue = {
    if (value == null) JNull
    else JString(value.asInstanceOf[SoQLVersion].value.toString)
  }

  override def numColumns(): Long = 1
}
