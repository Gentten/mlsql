package com.intigua.antlr4.autosuggest

import org.antlr.v4.runtime.Token
import org.scalatest.BeforeAndAfterEach
import tech.mlsql.autosuggest.meta.{MetaProvider, MetaTable, MetaTableColumn, MetaTableKey}
import tech.mlsql.autosuggest.statement.{LexerUtils, SuggestItem}
import tech.mlsql.autosuggest.{DataType, TokenPos, TokenPosType}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * 2/6/2020 WilliamZhu(allwefantasy@gmail.com)
 */
class AutoSuggestContextTest extends BaseTest with BeforeAndAfterEach {

  override def afterEach(): Unit = {
    context.statements.clear()
  }

  test("parse") {
    val wow = context.lexer.tokenizeNonDefaultChannel(
      """
        | -- yes
        | load hive.`` as -- jack
        | table1;
        | select * from table1 as table2;
        |""".stripMargin).tokens.asScala.toList
    context.build(wow)

    assert(context.statements.size == 2)

  }
  test("parse partial") {
    val wow = context.lexer.tokenizeNonDefaultChannel(
      """
        | -- yes
        | load hive.`` as -- jack
        | table1;
        | select * from table1
        |""".stripMargin).tokens.asScala.toList
    context.build(wow)
    printStatements(context.statements)
    assert(context.statements.size == 2)
  }

  def printStatements(items: ArrayBuffer[List[Token]]) = {
    items.foreach { item =>
      println(item.map(_.getText).mkString(" "))
      println()
    }
  }

  test("relative pos convert") {
    val wow = context.lexer.tokenizeNonDefaultChannel(
      """
        | -- yes
        | load hive.`` as -- jack
        | table1;
        | select * from table1
        |""".stripMargin).tokens.asScala.toList
    context.build(wow)

    assert(context.statements.size == 2)
    // select * f[cursor]rom table1
    val tokenPos = LexerUtils.toTokenPos(wow, 5, 11)
    assert(tokenPos == TokenPos(9, TokenPosType.CURRENT, 1))
    assert(context.toRelativePos(tokenPos)._1 == TokenPos(2, TokenPosType.CURRENT, 1))
  }

  test("keyword") {
    val wow = context.lexer.tokenizeNonDefaultChannel(
      """
        | -- yes
        | loa
        |""".stripMargin).tokens.asScala.toList
    context.build(wow)
    val tokenPos = LexerUtils.toTokenPos(wow, 3, 4)
    assert(tokenPos == TokenPos(0, TokenPosType.CURRENT, 3))
    assert(context.suggest(tokenPos)(0) == SuggestItem("load"))
  }

  test("spark sql") {
    val wow = context.rawSQLLexer.tokenizeNonDefaultChannel(
      """
        |SELECT CAST(25.65 AS int) from jack;
        |""".stripMargin).tokens.asScala.toList

    wow.foreach(item => println(s"${item.getText} ${item.getType}"))
  }

  test("load/select 4/10 select ke[cursor] from") {
    val wow = context.lexer.tokenizeNonDefaultChannel(
      """
        | -- yes
        | load hive.`jack.db` as table1;
        | select ke from (select keywords,search_num,c from table1) table2
        |""".stripMargin).tokens.asScala.toList
    val items = context.build(wow).suggest(LexerUtils.toTokenPos(wow, 4, 10))
    assert(items == List(SuggestItem("keywords")))
  }

  test("load/select 4/22 select  from (select [cursor]keywords") {
    context.setMetaProvider(new MetaProvider {
      override def search(key: MetaTableKey): Option[MetaTable] = {
        val key = MetaTableKey(None, None, "table1")
        val value = Option(MetaTable(
          key, List(
            MetaTableColumn("keywords", DataType.STRING, true, Map()),
            MetaTableColumn("search_num", DataType.STRING, true, Map()),
            MetaTableColumn("c", DataType.STRING, true, Map()),
            MetaTableColumn("d", DataType.STRING, true, Map())
          )
        ))
        value
      }
    })
    val wow = context.lexer.tokenizeNonDefaultChannel(
      """
        | -- yes
        | load hive.`jack.db` as table1;
        | select  from (select keywords,search_num,c from table1) table2
        |""".stripMargin).tokens.asScala.toList
    val items = context.build(wow).suggest(LexerUtils.toTokenPos(wow, 4, 22))
    assert(items == List(
      SuggestItem("table2"),
      SuggestItem("table1"),
      SuggestItem("keywords"),
      SuggestItem("search_num"),
      SuggestItem("c"), SuggestItem("d")))

  }
}


