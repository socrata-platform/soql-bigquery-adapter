package com.socrata.bq.query

import com.google.api.services.bigquery.model.TableRow
import com.rojoma.simplearm.util._
import com.socrata.datacoordinator.truth.loader.sql.AbstractRepBasedDataSqlizer
import com.socrata.datacoordinator.truth.sql.SqlColumnRep
import com.socrata.datacoordinator.{Row, MutableRow}
import com.socrata.datacoordinator.util.CloseableIterator
import com.socrata.datacoordinator.id.{ColumnId, UserColumnId}
import com.socrata.bq.soql.{SoQLBigQueryReadRep, BigQueryRepFactory, ParametricSql}
import com.socrata.soql.collection.OrderedMap
import com.socrata.soql.SoQLAnalysis
import com.socrata.soql.types.{SoQLText, SoQLValue}
import com.typesafe.scalalogging.slf4j.Logging
import java.sql.{SQLException, PreparedStatement, Connection, ResultSet}

import com.socrata.bq.soql.bqreps.TextRep

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable

trait DataSqlizerQuerier[CT, CV] extends AbstractRepBasedDataSqlizer[CT, CV] with Logging {
  this: AbstractRepBasedDataSqlizer[CT, CV] =>

  def query(conn: Connection, analysis: SoQLAnalysis[UserColumnId, CT],
               toSql: (SoQLAnalysis[UserColumnId, CT], String) => ParametricSql, // analsysis, tableName
               toRowCountSql: (SoQLAnalysis[UserColumnId, CT], String) => ParametricSql, // analsysis, tableName
               reqRowCount: Boolean,
               querySchema: OrderedMap[ColumnId, SqlColumnRep[CT, CV]],
               bqReps: Array[SoQLBigQueryReadRep[CT, CV]]) :
               CloseableIterator[com.socrata.datacoordinator.Row[CV]] with RowCount = {

    // get row count
    val rowCount: Option[Long] =
      if (reqRowCount) {
        using(executeSql(conn, toRowCountSql(analysis, dataTableName))) { rs =>
          try {
            rs.next()
            Some(rs.getLong(1))
          } finally {
            rs.getStatement.close()
          }
        }
      } else {
        None
      }

    // For some weird reason, when you iterate over the querySchema, a new Rep is created from scratch
    // every time, which is very expensive.  Move that out of the inner loop of decodeRow.
    // Also, the orderedMap is extremely inefficient and very complex to debug.
    // TODO: refactor PG server not to use Ordered Map.
    val decoders2 = querySchema.map { case (cid, rep) =>
      (cid, rep.fromResultSet(_, _), rep.physColumns.length)
    }.toArray

//    val decoders = querySchema.map { case (cid, rep) =>
//      (cid, rep.toSoQL(_, _))
//    }.toArray

    val repFactory = new BigQueryRepFactory

//    val test : SoQLBigQueryReadRep[CT, CV] = new TextRep("field0")

    val decoders = Array(Tuple2(new ColumnId(0), bqReps(0).toSoQL(_)))

    // get rows
    if (analysis.selection.size > 0) {
//      val rs = executeSql(conn, toSql(analysis, dataTableName))
//      Statement and resultset are closed by the iterator.
//      new ResultSetIt(rowCount, rs, decodeBigQueryRow(decoders))
      new BigQueryResultSetIt(Option(10), BigQueryQuerier.query("select field1 from [wilbur.test]"), decodeBigQueryRow(decoders))
    } else {
      logger.debug("Queried a dataset with no user columns")
      EmptyIt
    }
  }

  def decodeBigQueryRow(decoders: Array[(ColumnId, String => CV)])
                       (m : mutable.Buffer[String]): com.socrata.datacoordinator.Row[CV] = {

    val row = new MutableRow[CV]
    var i = 0

    decoders.foreach { case (cid, bqExtractor) =>
        row(cid) = bqExtractor(m(i))
        i += 1
    }
    row.freeze()
  }

  def decodeRow(decoders: Array[(ColumnId, (ResultSet, Int) => CV, Int)])(rs: ResultSet):
    com.socrata.datacoordinator.Row[CV] = {

    val row = new MutableRow[CV]
    var i = 1

    decoders.foreach { case (cid, rsExtractor, physColumnsLen) =>
      row(cid) = rsExtractor(rs, i)
      i += physColumnsLen
    }
    row.freeze()
  }

  private def executeSql(conn: Connection, pSql: ParametricSql): ResultSet = {
    try {
      logger.debug("sql: {}", pSql.sql)
      // Statement to be closed by caller
      val stmt = conn.prepareStatement(pSql.sql)
      // need to explicitly set a fetch size to stream results
      stmt.setFetchSize(1000)
      pSql.setParams.zipWithIndex.foreach { case (setParamFn, idx) =>
        setParamFn(Some(stmt), idx + 1)
      }
      stmt.executeQuery()
    } catch {
      case ex: SQLException =>
        logger.error(s"SQL Exception on ${pSql}")
        throw ex
    }
  }

  class ResultSetIt(val rowCount: Option[Long], rs: ResultSet, toRow: (ResultSet) => com.socrata.datacoordinator.Row[CV])
    extends CloseableIterator[com.socrata.datacoordinator.Row[CV]] with RowCount {

    private var _hasNext: Option[Boolean] = None

    def hasNext: Boolean = {
      _hasNext = _hasNext match {
        case Some(b) => _hasNext
        case None => Some(rs.next())
      }
      _hasNext.get
    }

    def next(): com.socrata.datacoordinator.Row[CV] = {
      if (hasNext) {
        val row = toRow(rs)
        _hasNext = None
        row
      } else {
        throw new Exception("No more data for the iterator.")
      }
    }

    def close() {
      try {
        rs.getStatement().close()
      } finally {
        rs.close()
      }
    }
  }

  class BigQueryResultSetIt(val rowCount : Option[Long], rows: ArrayBuffer[mutable.Buffer[String]], toRow: (mutable.Buffer[String] => Row[CV]))
    extends CloseableIterator[com.socrata.datacoordinator.Row[CV]] with RowCount {

    private val it: Iterator[mutable.Buffer[String]] = rows.iterator
    private var _hasNext: Boolean = it.hasNext

    override def next(): Row[CV] = {
      if (hasNext) {
        val upNext = it.next
        _hasNext = it.hasNext
        toRow(upNext)
      } else {
        throw new Exception("No more data for the BigQueryResultSetIt")
      }
    }

    override def hasNext: Boolean = {
      _hasNext
    }

    override def close(): Unit = {}
  }

  object EmptyIt extends CloseableIterator[Nothing] with RowCount {
    val rowCount = Some(0L)
    def hasNext = false
    def next() = throw new Exception("Called next() on an empty iterator")
    def close() {}
  }
}

trait RowCount {

  val rowCount: Option[Long]

}