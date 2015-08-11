package com.socrata.bq.soql


import com.socrata.datacoordinator.id.UserColumnId
import com.socrata.datacoordinator.truth.sql.SqlColumnRep
import com.socrata.bq.store.PostgresUniverseCommon
import com.socrata.soql.SoQLAnalysis
import com.socrata.soql.types._
import com.socrata.soql.typed.{StringLiteral, OrderBy, CoreExpr}
import scala.util.parsing.input.NoPosition


class SoQLAnalysisSqlizer(analysis: SoQLAnalysis[UserColumnId, SoQLType], tableName: String, allColumnReps: Seq[SqlColumnRep[SoQLType, SoQLValue]])
      extends Sqlizer[Tuple3[SoQLAnalysis[UserColumnId, SoQLType], String, Seq[SqlColumnRep[SoQLType, SoQLValue]]]] {

  import Sqlizer._
  import SqlizerContext._

  val underlying = Tuple3(analysis, tableName, allColumnReps)
  val convToUnderScore = "[)./+\\-?(*<> ]"

  def sql(rep: Map[UserColumnId, SqlColumnRep[SoQLType, SoQLValue]], setParams: Seq[String], ctx: Context, escape: Escape) = {
    sql(false, rep, setParams, ctx, escape)
  }

  /**
   * For rowcount w/o group by, just replace the select with count(*).
   * For rowcount with group by, wrap the original group by sql with a select count(*) from ( {original}) t1
   */
  def rowCountSql(rep: Map[UserColumnId, SqlColumnRep[SoQLType, SoQLValue]], setParams: Seq[String], ctx: Context, escape: Escape) = {
    sql(true, rep, setParams, ctx, escape)
  }

  private def sql(reqRowCount: Boolean,
                  rep: Map[UserColumnId, SqlColumnRep[SoQLType, SoQLValue]],
                  setParams: Seq[String],
                  context: Context,
                  escape: Escape) = {

    val ana = if (reqRowCount) rowCountAnalysis(analysis) else analysis
    val ctx = context + (Analysis -> analysis)

    // SELECT
    val ctxSelect = ctx + (SoqlPart -> SoqlSelect)
    val (selectPhrase, setParamsSelect) =
      if (reqRowCount && analysis.groupBy.isEmpty) (Seq("count(*)"), setParams)
      else select(rep, setParams, ctxSelect, escape)

    // WHERE
    val where = ana.where.map(_.sql(rep, setParamsSelect, ctx + (SoqlPart -> SoqlWhere), escape))
    val setParamsWhere = where.map(_.setParams).getOrElse(setParamsSelect)

    // SEARCH
    val search = ana.search.map { search =>
      val searchLit = StringLiteral(search, SoQLText)(NoPosition)
      val BQSql(searchSql, searchSetParams) = searchLit.sql(rep, setParamsWhere, ctx + (SoqlPart -> SoqlSearch), escape)

      PostgresUniverseCommon.searchVector(allColumnReps) match {
        case Some(sv) =>
          val andOrWhere = if (where.isDefined) " AND" else " WHERE"
          val fts = s"$andOrWhere $sv @@ plainto_tsquery('english', $searchSql)"
          BQSql(fts, searchSetParams)
        case None =>
          BQSql("", setParamsWhere)
      }
    }
    val setParamsSearch = search.map(_.setParams).getOrElse(setParamsWhere)

    // GROUP BY
    val groupBy = ana.groupBy.map { (groupBys: Seq[CoreExpr[UserColumnId, SoQLType]]) =>
      groupBys.foldLeft(Tuple2(Seq.empty[String], setParamsSearch)) { (t2, gb: CoreExpr[UserColumnId, SoQLType]) =>
      val BQSql(sql, newSetParams) = gb.sql(rep, t2._2, ctx + (SoqlPart -> SoqlGroup), escape)
        val modifiedSQL = sql.replaceAll(convToUnderScore, "_") // to reference possible aliases in the SELECT stmt
      (t2._1 :+ modifiedSQL, newSetParams)
    }}
    val setParamsGroupBy = groupBy.map(_._2).getOrElse(setParamsSearch)

    // HAVING
    val having = ana.having.map(_.sql(rep, setParamsGroupBy, ctx + (SoqlPart -> SoqlHaving), escape))
    val setParamsHaving = having.map(_.setParams).getOrElse(setParamsGroupBy)

    // ORDER BY
    val orderBy = ana.orderBy.map { (orderBys: Seq[OrderBy[UserColumnId, SoQLType]]) =>
      orderBys.foldLeft(Tuple2(Seq.empty[String], setParamsHaving)) { (t2, ob: OrderBy[UserColumnId, SoQLType]) =>
        val BQSql(sql, newSetParams) =
          ob.sql(rep, t2._2, ctx + (SoqlPart -> SoqlOrder) + (RootExpr -> ob.expression), escape)
        val modifiedSQL = sql.replaceAll(convToUnderScore, "_") // to reference possible aliases in the SELECT stmt
        (t2._1 :+ modifiedSQL, newSetParams)
      }}

    val setParamsOrderBy = orderBy.map(_._2).getOrElse(setParamsHaving)

    // COMPLETE SQL
    val completeSql = funcAlias(selectPhrase).mkString("SELECT ", ",", "") +
      s" FROM $tableName" +
      where.map(" WHERE " +  _.sql).getOrElse("") +
      search.map(_.sql).getOrElse("") +
      groupBy.map(_._1.mkString(" GROUP BY ", ",", "")).getOrElse("") +
      having.map(" HAVING " +  _.sql).getOrElse("") +
      orderBy.map(_._1.mkString(" ORDER BY ", ",", "")).getOrElse("") +
      ana.limit.map(" LIMIT " + _.toString).getOrElse("") +
      ana.offset.map(" OFFSET " + _.toString).getOrElse("")

    BQSql(countBySubQuery(reqRowCount, completeSql), setParamsOrderBy)
  }

  private def countBySubQuery(reqRowCount: Boolean, sql: String) = {
    if (reqRowCount && analysis.groupBy.isDefined) s"SELECT count(*) FROM ($sql) t1"
    else sql
  }

  private def select(rep: Map[UserColumnId, SqlColumnRep[SoQLType, SoQLValue]],
                     setParams: Seq[String],
                     ctx: Context,
                     escape: Escape) = {
    analysis.selection.foldLeft(Tuple2(Seq.empty[String], setParams)) { (t2, columnNameAndcoreExpr) =>
      val (columnName, coreExpr) = columnNameAndcoreExpr
      val BQSql(sql, newSetParams) = coreExpr.sql(rep, t2._2, ctx + (RootExpr -> coreExpr), escape)
      val soqlType = coreExpr.typ.toString

      val timeStamp = """.*timestamp""".r

      // match regex is there so that the TIMESTAMP_TO_USEC method does not get called when
      // the query wants to extract the day, year, or month from the timestamp.
      val conversion = soqlType match {
        case timeStamp() => if (!sql.matches(".*(day|year|month).*")) s"TIMESTAMP_TO_USEC($sql)" else sql
        case "point" => s"$sql.lat, $sql.long"
        case _ => sql
      }
      (t2._1 :+ conversion, newSetParams)
    }
  }

  /**
   * Maps each function in the select statement so that it can be referenced outside of the SELECT clause
   * statement if it is present there.
   */
  private def funcAlias(select: Seq[String]): Seq[String] = {
    select.map(sql =>
      if(sql.matches(".*(sum|avg|count|min|max|floor|abs|day|hour|year|length|cast).*")) {
        s"$sql AS ${sql.replaceAll(convToUnderScore, "_")}"
      } else {
        sql
      })
  }

  /**
   * Basically, analysis for row count has select, limit and offset removed.
   * TODO: select count(*) // w/o group by which result is always 1.
   * @param a original query analysis
   * @return Analysis for generating row count sql.
   */
  private def rowCountAnalysis(a: SoQLAnalysis[UserColumnId, SoQLType]): SoQLAnalysis[UserColumnId, SoQLType] =
    a.copy(selection = a.selection.empty, orderBy =  None, limit = None, offset = None)

}
