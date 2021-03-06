package com.socrata.bq.soql

import com.socrata.soql.typed._
import com.socrata.soql.types._
import com.socrata.datacoordinator.id.UserColumnId
import Sqlizer._
import SqlizerContext._

class StringLiteralSqlizer(lit: StringLiteral[SoQLType]) extends Sqlizer[StringLiteral[SoQLType]] {

  val underlying = lit

  @Override
  def sql(physicalColumnMapping: Map[UserColumnId, String], setParams: Seq[String], ctx: Context, escape: Escape) = {
    ctx.get(SoqlPart) match {
      case Some(SoqlHaving) | Some(SoqlGroup) =>
        val v = toUpper(quote(lit.value, escape, ctx), ctx)
        BQSql(v, setParams)
      case Some(SoqlSelect) | Some(SoqlOrder) if usedInGroupBy(ctx) =>
        val v = toUpper(quote(lit.value, escape, ctx), ctx)
        BQSql(v, setParams)
      case _ =>
        BQSql(ParamPlaceHolder, setParams :+ toUpper(quote(lit.value, escape, ctx), ctx))
    }
  }

  private def quote(s: String, escape: Escape, ctx: Context) = {
    ctx.get(Extras) match {
      case Some(BeginsWith) => s"'${escape(s)}%'"
      case _ => s"'${escape(s)}'"
    }
  }

  private def toUpper(lit: String, ctx: Context): String = if (useUpper(ctx)) lit.toUpperCase else lit

}

class NumberLiteralSqlizer(lit: NumberLiteral[SoQLType]) extends Sqlizer[NumberLiteral[SoQLType]] {

  val underlying = lit

  def sql(physicalColumnMapping: Map[UserColumnId, String], setParams: Seq[String], ctx: Context, escape: Escape) = {

    ctx.get(SoqlPart) match {
      case Some(SoqlHaving) | Some(SoqlGroup) =>
        BQSql(lit.value.bigDecimal.toPlainString, setParams)
      case Some(SoqlSelect) | Some(SoqlOrder) if usedInGroupBy(ctx) =>
        BQSql(lit.value.bigDecimal.toPlainString, setParams)
      case _ =>
        BQSql(ParamPlaceHolder, setParams :+ lit.value.toString())
    }
  }
}

class BooleanLiteralSqlizer(lit: BooleanLiteral[SoQLType]) extends Sqlizer[BooleanLiteral[SoQLType]] {

  val underlying = lit

  def sql(physicalColumnMapping: Map[UserColumnId, String], setParams: Seq[String], ctx: Context, escape: Escape) = {

    ctx.get(SoqlPart) match {
      case Some(SoqlHaving) | Some(SoqlGroup) =>
        BQSql(lit.value.toString, setParams)
      case Some(SoqlSelect) | Some(SoqlOrder) if usedInGroupBy(ctx) =>
        BQSql(lit.value.toString, setParams)
      case _ =>
        BQSql(ParamPlaceHolder, setParams :+ lit.value.toString)
    }
  }
}

class NullLiteralSqlizer(lit: NullLiteral[SoQLType]) extends Sqlizer[NullLiteral[SoQLType]] {

  val underlying = lit

  def sql(physicalColumnMapping: Map[UserColumnId, String], setParams: Seq[String], ctx: Context, escape: Escape) =
    BQSql("null", setParams)
}

class FunctionCallSqlizer(expr: FunctionCall[UserColumnId, SoQLType]) extends Sqlizer[FunctionCall[UserColumnId, SoQLType]] {

  val underlying = expr

  def sql(physicalColumnMapping: Map[UserColumnId, String], setParams: Seq[String], ctx: Context, escape: Escape) = {
    val fn = SqlFunctions(expr.function.function)
    val BQSql(sql, fnSetParams) = fn(physicalColumnMapping, expr, setParams, ctx, escape)
    // SoQL parsing bakes parenthesis into the ast tree without explicitly spitting out parenthesis.
    // We add parenthesis to every function call to preserve semantics.

    // However, specifically with extent, we don't want to wrap the call in parenthesis or else BQ errors
    val funcName = expr.function.function.identity
    funcName match {
      case "extent" => BQSql(sql, fnSetParams)
      case _ => BQSql(s"($sql)", fnSetParams)
    }
  }
}

class ColumnRefSqlizer(expr: ColumnRef[UserColumnId, SoQLType]) extends Sqlizer[ColumnRef[UserColumnId, SoQLType]] {

  val underlying = expr

  // Convert all user column ids to physical column names to reference the columns as stored in BigQuery.
  def sql(physicalColumnMapping: Map[UserColumnId, String], setParams: Seq[String], ctx: Context, escape: Escape) = {
    BQSql(physicalColumnMapping(expr.column), setParams)
  }
}
