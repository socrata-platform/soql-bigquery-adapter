package com.socrata.bq.soql

import com.socrata.soql.typed.OrderBy
import com.socrata.datacoordinator.id.UserColumnId
import com.socrata.datacoordinator.truth.sql.SqlColumnRep
import com.socrata.soql.types.{SoQLValue, SoQLType}
import com.socrata.soql.types.SoQLID.{StringRep => SoQLIDRep}
import com.socrata.soql.types.SoQLVersion.{StringRep => SoQLVersionRep}


class OrderBySqlizer(orderBy: OrderBy[UserColumnId, SoQLType]) extends Sqlizer[OrderBy[UserColumnId, SoQLType]] {

  import Sqlizer._

  val underlying = orderBy

  def sql(physicalColumnMapping: Map[UserColumnId, String], setParams: Seq[String], ctx: Context, escape: Escape) = {
    val BQSql(s, setParamsOrderBy) = orderBy.expression.sql(physicalColumnMapping, setParams, ctx, escape)
    val se = s + (if (orderBy.ascending) "" else " desc")
    BQSql(se, setParamsOrderBy)
  }
}

