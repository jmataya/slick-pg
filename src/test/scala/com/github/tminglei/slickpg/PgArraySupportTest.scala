package com.github.tminglei.slickpg

import java.sql.{Timestamp, Time, Date}

import org.junit._
import org.junit.Assert._
import java.util.UUID

import scala.collection.mutable.Buffer
import slick.driver.PostgresDriver
import slick.jdbc.GetResult

import scala.concurrent.ExecutionContext.Implicits.global

class PgArraySupportTest {
  import utils.SimpleArrayUtils._

  //-- additional definitions
  case class Institution(value: Long)
  case class MarketFinancialProduct(value: String)

  object MyPostgresDriver1 extends PostgresDriver with PgArraySupport {
    override val api = new API with ArrayImplicits with MyArrayImplicitsPlus {}

    ///
    trait MyArrayImplicitsPlus {
      implicit val simpleLongBufferTypeMapper = new SimpleArrayJdbcType[Long]("int8").to(_.toBuffer)
      implicit val simpleStrVectorTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toVector)
      implicit val institutionListTypeWrapper =  new SimpleArrayJdbcType[Institution]("int8")
        .basedOn[Long](_.value, new Institution(_)).to(_.toList)
      implicit val marketFinancialProductWrapper = new SimpleArrayJdbcType[MarketFinancialProduct]("text")
        .basedOn[String](_.value, new MarketFinancialProduct(_)).to(_.toList)
      ///
      implicit val advancedStringListTypeMapper = new AdvancedArrayJdbcType[String]("text",
        fromString(identity)(_).orNull, mkString(identity))
    }
  }

  //////////////////////////////////////////////////////////////////////////
  import MyPostgresDriver1.api._

  val db = Database.forURL(url = dbUrl, driver = "org.postgresql.Driver")

  case class ArrayBean(
    id: Long,
    intArr: List[Int],
    longArr: Buffer[Long],
    shortArr: List[Short],
    strList: List[String],
    strArr: Option[Vector[String]],
    uuidArr: List[UUID],
    institutions: List[Institution],
    mktFinancialProducts: Option[List[MarketFinancialProduct]]
    )

  class ArrayTestTable(tag: Tag) extends Table[ArrayBean](tag, "ArrayTest") {
    def id = column[Long]("id", O.AutoInc, O.PrimaryKey)
    def intArr = column[List[Int]]("intArray", O.Default(Nil))
    def longArr = column[Buffer[Long]]("longArray")
    def shortArr = column[List[Short]]("shortArray")
    def strList = column[List[String]]("stringList")
    def strArr = column[Option[Vector[String]]]("stringArray")
    def uuidArr = column[List[UUID]]("uuidArray")
    def institutions = column[List[Institution]]("institutions")
    def mktFinancialProducts = column[Option[List[MarketFinancialProduct]]]("mktFinancialProducts")

    def * = (id, intArr, longArr, shortArr, strList, strArr, uuidArr, institutions, mktFinancialProducts) <> (ArrayBean.tupled, ArrayBean.unapply)
  }
  val ArrayTests = TableQuery[ArrayTestTable]

  //------------------------------------------------------------------------------

  val uuid1 = UUID.randomUUID()
  val uuid2 = UUID.randomUUID()
  val uuid3 = UUID.randomUUID()

  val testRec1 = ArrayBean(33L, List(101, 102, 103), Buffer(1L, 3L, 5L, 7L), List(1,7), List("robert}; drop table students--"),
    Some(Vector("str1", "str3")), List(uuid1, uuid2), List(Institution(113)), None)
  val testRec2 = ArrayBean(37L, List(101, 103), Buffer(11L, 31L, 5L), Nil, List(""),
    Some(Vector("str11", "str3")), List(uuid1, uuid2, uuid3), List(Institution(579)), Some(List(MarketFinancialProduct("product1"))))
  val testRec3 = ArrayBean(41L, List(103, 101), Buffer(11L, 5L, 31L), List(35,77), Nil,
    Some(Vector("(s)", "str5", "str3")), List(uuid1, uuid3), Nil, Some(List(MarketFinancialProduct("product3"), MarketFinancialProduct("product x"))))

  @Test
  def testArrayFunctions(): Unit = {
    db.run(DBIO.seq(
      ///-- setup
      (ArrayTests.schema) create,
      //
      ArrayTests forceInsertAll List(testRec1, testRec2, testRec3),
      // 0. simple list
      ArrayTests.sortBy(_.id).to[List].result.map(
        assertEquals(List(testRec1, testRec2, testRec3), _)
      ),
      // 1. 'any'
      ArrayTests.filter(101.bind === _.intArr.any).sortBy(_.id).to[List].result.map(
        assertEquals(List(testRec1, testRec2, testRec3), _)
      ),
      // 2. 'all'
      ArrayTests.filter(5L.bind <= _.longArr.all).sortBy(_.id).to[List].result.map(
        assertEquals(List(testRec2, testRec3), _)
      ),
      // 3. '@>'
      ArrayTests.filter(_.strArr @> Vector("str3")).sortBy(_.id).to[List].result.map(
        assertEquals(List(testRec1, testRec2, testRec3), _)
      ),
      // 3.1. '@>'
      ArrayTests.filter(_.strArr @> Vector("str3").bind).sortBy(_.id).to[List].result.map(
        assertEquals(List(testRec1, testRec2, testRec3), _)
      ),
      // 4. '<@'
      ArrayTests.filter(Vector("str3").bind <@: _.strArr).sortBy(_.id).to[List].result.map(
        assertEquals(List(testRec1, testRec2, testRec3), _)
      ),
      // 5. '&&'
      ArrayTests.filter(_.longArr @& Buffer(5L, 17L).bind).sortBy(_.id).to[List].result.map(
        assertEquals(List(testRec1, testRec2, testRec3), _)
      ),
      // 6. 'length'
      ArrayTests.filter(_.longArr.length() > 3.bind).sortBy(_.id).to[List].result.map(
        assertEquals(List(testRec1), _)
      ),
      // 7. 'unnest'
      ArrayTests.filter(5L.bind <= _.longArr.all).map(_.strArr.unnest).to[List].result.map(
        r => assertEquals((testRec2.strArr.get ++ testRec3.strArr.get).toList, r.map(_.orNull))
      ),
      // 8. 'concatenate'
      ArrayTests.filter(_.id === 33L.bind).map(_.intArr ++ List(105, 107).bind).result.head.map(
        assertEquals(List(101, 102, 103, 105, 107), _)
      ),
      // 8.1. 'concatenate'
      ArrayTests.filter(_.id === 33L.bind).map(List(105, 107).bind ++ _.intArr).result.head.map(
        assertEquals(List(105, 107, 101, 102, 103), _)
      ),
      // 8.2 'concatenate'
      ArrayTests.filter(_.id === 33L.bind).map(_.intArr + 105.bind).result.head.map(
        assertEquals(List(101, 102, 103, 105), _)
      ),
      // 8.3 'concatenate'
      ArrayTests.filter(_.id === 33L.bind).map(105.bind +: _.intArr).result.head.map(
        assertEquals(List(105, 101, 102, 103), _)
      ),
      ///-- clearup
      (ArrayTests.schema) drop
    ).transactionally)
  }

  //------------------------------------------------------------------------

  @Test
  def testPlainArrayFunctions(): Unit = {
    case class ArrayBean1(
      id: Long,
      uuidArr: List[UUID],
      strArr: Seq[String],
      longArr: Seq[Long],
      intArr: List[Int],
      shortArr: Vector[Short],
      floatArr: List[Float],
      doubleArr: List[Double],
      boolArr: Seq[Boolean],
      dateArr: List[Date],
      timeArr: List[Time],
      tsArr: Seq[Timestamp]
      )

    import MyPlainPostgresDriver.plainAPI._

    implicit val getArrarBean1Result = GetResult(r =>
      ArrayBean1(r.nextLong(),
        r.nextArray[UUID]().toList,
        r.nextArray[String](),
        r.nextArray[Long](),
        r.nextArray[Int]().toList,
        r.nextArray[Short]().to[Vector],
        r.nextArray[Float]().toList,
        r.nextArray[Double]().toList,
        r.nextArray[Boolean](),
        r.nextArray[Date]().toList,
        r.nextArray[Time]().toList,
        r.nextArray[Timestamp]()
      )
    )

    val b = ArrayBean1(101L, List(UUID.randomUUID()), List("tewe", "ttt"), List(111L), List(1, 2), Vector(3, 5), List(1.2f, 43.32f), List(21.35d), List(true, true),
      List(new Date(System.currentTimeMillis())), List(new Time(System.currentTimeMillis())), List(new Timestamp(System.currentTimeMillis())))

    db.run(DBIO.seq(
      sqlu"""create table ArrayTest1(
            |   id int8 not null primary key,
            |   uuid_arr uuid[] not null,
            |   str_arr text[] not null,
            |   long_arr int8[] not null,
            |   int_arr int4[] not null,
            |   short_arr int2[] not null,
            |   float_arr float4[] not null,
            |   double_arr float8[] not null,
            |   bool_arr bool[] not null,
            |   date_arr date[] not null,
            |   time_arr time[] not null,
            |   ts_arr timestamp[] not null)
          """,
      ///
      sqlu"insert into ArrayTest1 values(${b.id}, ${b.uuidArr}, ${b.strArr}, ${b.longArr}, ${b.intArr}, ${b.shortArr}, ${b.floatArr}, ${b.doubleArr}, ${b.boolArr}, ${b.dateArr}, ${b.timeArr}, ${b.tsArr})",
      sql"select * from ArrayTest1 where id = ${b.id}".as[ArrayBean1].head.map(
        f => {
          b.uuidArr.zip(f.uuidArr).map(r => assertEquals(r._1, r._2))
          b.strArr.zip(f.strArr).map(r => assertEquals(r._1, r._2))
          b.longArr.zip(f.longArr).map(r => assertEquals(r._1, r._2))
          b.intArr.zip(f.intArr).map(r => assertEquals(r._1, r._2))
          b.shortArr.zip(f.shortArr).map(r => assertEquals(r._1, r._2))
          b.floatArr.zip(f.floatArr).map(r => assertEquals(r._1, r._2, 0.01f))
          b.doubleArr.zip(f.doubleArr).map(r => assertEquals(r._1, r._2, 0.01d))
          b.boolArr.zip(f.boolArr).map(r => assertEquals(r._1, r._2))
          b.dateArr.zip(f.dateArr).map(r => assertEquals(r._1.toString, r._2.toString))
          b.timeArr.zip(f.timeArr).map(r => assertEquals(r._1.toString, r._2.toString))
          b.tsArr.zip(f.tsArr).map(r => assertEquals(r._1.toString, r._2.toString))
        }
      ),
      ///
      sqlu"drop table if exists ArrayTest1 cascade"
    ).transactionally)
  }
}
