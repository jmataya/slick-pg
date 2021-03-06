package com.github.tminglei.slickpg

import java.util.UUID
import slick.driver.PostgresDriver
import java.sql.{Timestamp, Time, Date}
import scala.reflect.runtime.{universe => u}
import slick.jdbc.{PositionedResult, JdbcType}

trait PgArraySupport extends array.PgArrayExtensions with array.PgArrayJdbcTypes { driver: PostgresDriver =>
  import driver.api._

  /// alias
  trait ArrayImplicits extends SimpleArrayImplicits

  trait SimpleArrayImplicits {
    /** for type/name, @see [[org.postgresql.core.Oid]] and [[org.postgresql.jdbc2.TypeInfoCache]]*/
    implicit val simpleUUIDListTypeMapper = new SimpleArrayJdbcType[UUID]("uuid").to(_.toList)
    implicit val simpleStrListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)
    implicit val simpleLongListTypeMapper = new SimpleArrayJdbcType[Long]("int8").to(_.toList)
    implicit val simpleIntListTypeMapper = new SimpleArrayJdbcType[Int]("int4").to(_.toList)
    implicit val simpleShortListTypeMapper = new SimpleArrayJdbcType[Short]("int2").to(_.toList)
    implicit val simpleFloatListTypeMapper = new SimpleArrayJdbcType[Float]("float4").to(_.toList)
    implicit val simpleDoubleListTypeMapper = new SimpleArrayJdbcType[Double]("float8").to(_.toList)
    implicit val simpleBoolListTypeMapper = new SimpleArrayJdbcType[Boolean]("bool").to(_.toList)
    implicit val simpleDateListTypeMapper = new SimpleArrayJdbcType[Date]("date").to(_.toList)
    implicit val simpleTimeListTypeMapper = new SimpleArrayJdbcType[Time]("time").to(_.toList)
    implicit val simpleTsListTypeMapper = new SimpleArrayJdbcType[Timestamp]("timestamp").to(_.toList)

    ///
    implicit def simpleArrayColumnExtensionMethods[B1, SEQ[B1] <: Seq[B1]](c: Rep[SEQ[B1]])(
      implicit tm: JdbcType[B1], tm1: JdbcType[SEQ[B1]]) = {
        new ArrayColumnExtensionMethods[B1, SEQ, SEQ[B1]](c)
      }
    implicit def simpleArrayOptionColumnExtensionMethods[B1, SEQ[B1] <: Seq[B1]](c: Rep[Option[SEQ[B1]]])(
      implicit tm: JdbcType[B1], tm1: JdbcType[SEQ[B1]]) = {
        new ArrayColumnExtensionMethods[B1, SEQ, Option[SEQ[B1]]](c)
      }
  }

  /// static sql support, NOTE: no extension methods available for static sql usage
  trait SimpleArrayPlainImplicits {
    import utils.PlainSQLUtils._
    {
      addNextArrayConverter((r) => simpleNextArray[Int](r).map(_.map(_.toShort)))
    }

    implicit class PgArrayPositionedResult(r: PositionedResult) {
      def nextArray[T]()(implicit tpe: u.TypeTag[T]): Seq[T] = nextArrayOption[T]().getOrElse(Nil)
      def nextArrayOption[T]()(implicit ttag: u.TypeTag[T]): Option[Seq[T]] = {
        val (matched, result) = extNextArray(u.typeOf[T], r)
        (if (matched) result else nextArrayConverters.get(u.typeOf[T].toString).map(_.apply(r))
          .getOrElse(simpleNextArray[T](r))).asInstanceOf[Option[Seq[T]]]
      }
    }

    /**
     * pls override this when you need additional array support
     * @return (matched, result)
     **/
    @deprecated(message = "pls use `PlainSQLUtils.addNextArrayConverter` instead", since = "0.10")
    protected def extNextArray(tpe: u.Type, r: PositionedResult): (Boolean, Option[Seq[_]]) =
      tpe match {
        case _ => (false, None)
      }

    private def simpleNextArray[T](r: PositionedResult): Option[Seq[T]] = {
      val value = r.rs.getArray(r.skip.currentPos)
      if (r.rs.wasNull) None else Some(
        value.getArray.asInstanceOf[Array[Any]].map(_.asInstanceOf[T]))
    }

    //////////////////////////////////////////////////////////////////////////
    implicit val getUUIDArray = mkGetResult(_.nextArray[UUID]())
    implicit val getUUIDArrayOption = mkGetResult(_.nextArrayOption[UUID]())
    implicit val setUUIDArray = mkArraySetParameter[UUID]("uuid")
    implicit val setUUIDArrayOption = mkArrayOptionSetParameter[UUID]("uuid")
    ///
    implicit val getStringArray = mkGetResult(_.nextArray[String]())
    implicit val getStringArrayOption = mkGetResult(_.nextArrayOption[String]())
    implicit val setStringArray = mkArraySetParameter[String]("text")
    implicit val setStringArrayOption = mkArrayOptionSetParameter[String]("text")
    ///
    implicit val getLongArray = mkGetResult(_.nextArray[Long]())
    implicit val getLongArrayOption = mkGetResult(_.nextArrayOption[Long]())
    implicit val setLongArray = mkArraySetParameter[Long]("int8")
    implicit val setLongArrayOption = mkArrayOptionSetParameter[Long]("int8")
    ///
    implicit val getIntArray = mkGetResult(_.nextArray[Int]())
    implicit val getIntArrayOption = mkGetResult(_.nextArrayOption[Int]())
    implicit val setIntArray = mkArraySetParameter[Int]("int4")
    implicit val setIntArrayOption = mkArrayOptionSetParameter[Int]("int4")
    ///
    implicit val getShortArray = mkGetResult(_.nextArray[Short]())
    implicit val getShortArrayOption = mkGetResult(_.nextArrayOption[Short]())
    implicit val setShortArray = mkArraySetParameter[Short]("int2")
    implicit val setShortArrayOption = mkArrayOptionSetParameter[Short]("int2")
    ///
    implicit val getFloatArray = mkGetResult(_.nextArray[Float]())
    implicit val getFloatArrayOption = mkGetResult(_.nextArrayOption[Float]())
    implicit val setFloatArray = mkArraySetParameter[Float]("float4")
    implicit val setFloatArrayOption = mkArrayOptionSetParameter[Float]("float4")
    ///
    implicit val getDoubleArray = mkGetResult(_.nextArray[Double]())
    implicit val getDoubleArrayOption = mkGetResult(_.nextArrayOption[Double]())
    implicit val setDoubleArray = mkArraySetParameter[Double]("float8")
    implicit val setDoubleArrayOption = mkArrayOptionSetParameter[Double]("float8")
    ///
    implicit val getBoolArray = mkGetResult(_.nextArray[Boolean]())
    implicit val getBoolArrayOption = mkGetResult(_.nextArrayOption[Boolean]())
    implicit val setBoolArray = mkArraySetParameter[Boolean]("bool")
    implicit val setBoolArrayOption = mkArrayOptionSetParameter[Boolean]("bool")
    ///
    implicit val getDateArray = mkGetResult(_.nextArray[Date]())
    implicit val getDateArrayOption = mkGetResult(_.nextArrayOption[Date]())
    implicit val setDateArray = mkArraySetParameter[Date]("date")
    implicit val setDateArrayOption = mkArrayOptionSetParameter[Date]("date")
    ///
    implicit val getTimeArray = mkGetResult(_.nextArray[Time]())
    implicit val getTimeArrayOption = mkGetResult(_.nextArrayOption[Time]())
    implicit val setTimeArray = mkArraySetParameter[Time]("time")
    implicit val setTimeArrayOption = mkArrayOptionSetParameter[Time]("time")
    ///
    implicit val getTimestampArray = mkGetResult(_.nextArray[Timestamp]())
    implicit val getTimestampArrayOption = mkGetResult(_.nextArrayOption[Timestamp]())
    implicit val setTimestampArray = mkArraySetParameter[Timestamp]("timestamp")
    implicit val setTimestampArrayOption = mkArrayOptionSetParameter[Timestamp]("timestamp")
  }
}
