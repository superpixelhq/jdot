package com.superpixel.advokit

import java.util.regex.Pattern
import scala.util.matching.Regex

abstract sealed trait JsonPath;

case object JsonTerminus extends JsonPath;
case class JsonValueAccess(val key: String, val childPath: JsonPath) extends JsonPath;
case class JsonObjectAccess(val key: String, val childPath: JsonPath) extends JsonPath;
case class JsonLink(val linkedChildPath: JsonPath) extends JsonPath



abstract sealed trait ExpressionType {
  val keyStr = "key";
  def patterns: Seq[Regex];
}
case object StartAccessExpression extends ExpressionType {
  override val patterns = Seq(
    keyStr.r
  )
}
case object AccessExpression extends ExpressionType {
  override val patterns = Seq(
    ("""\.""" + keyStr).r,
    ("""\[""" + keyStr + """\]""").r
  )
};
case object LinkExpression extends ExpressionType {
  override val patterns = Seq(
    """\>""".r
  )
}


class JsonPathException(message: String, pathString: String, cause: Throwable = null) 
  extends RuntimeException(s"$message IN: '$pathString'", cause)


object JsonPath {
  
  val DELIMS = Seq(""".""", """[""", """]""", """>""")
  private val IS_DELIMITER_STR = """[""" + DELIMS.map(Pattern.quote(_)).mkString + """]"""
  val IS_DELIMITER_REGEX = (IS_DELIMITER_STR).r
  private val UNESCAPED_DELIMS_STR = """(?<=(?<!\\)(?:\\{2}){0,10})[""" + DELIMS.map(Pattern.quote(_)).mkString + """]"""
  
  val ESCAPED_DELIM_REGEX = ("""(?<=(\\)""" + IS_DELIMITER_STR + """)""").r
  val UNESCAPED_DELIM_REGEX = UNESCAPED_DELIMS_STR.r
  private val WITH_DELIMITER_REGEX_STR = {
    """((?<=""" + UNESCAPED_DELIMS_STR + """)|(?=""" + UNESCAPED_DELIMS_STR + """))"""
  }
  
  def unescapeJsonKey(key: String): String = {
    ESCAPED_DELIM_REGEX.replaceAllIn(key, """$1""")
  }
  
  def escapeJsonKey(key: String): String = {
    UNESCAPED_DELIM_REGEX.replaceAllIn(key, """\$1""")
  }
  
  /***
   * Takes path string, validates and converts in to JsonPath tree.
   * 
   * apply method cann be called from an object as follows
   *  JsonParser(pathString)
   *  JsonParser.apply(pathString)
   */
  @throws(classOf[JsonPathException])
  def apply(pathString: String): JsonPath = {
    
    /***
     * Takes expression type tuples in reverse order and adds them tail recursively 
     * into a JsonPath tree.
     * Expression type is mapped to a JsonPath class, extracting the relevent information 
     * from the expression sequence included.
     * 
     */
    def jsonPathConstructor(ls: Seq[(ExpressionType, Seq[String])], acc: JsonPath = JsonTerminus): JsonPath = ls match {

      case (StartAccessExpression, exprSeq) +: Nil => 
        constructAccessPathLayer(acc, extractFirstFieldKey(exprSeq))
      case (_, exprSeq) +: Nil => throw new JsonPathException("Json stringPath does not start correctly, the first expression must be a json key", pathString)
      
      case (AccessExpression, exprSeq) +: tl => 
        jsonPathConstructor(tl, constructAccessPathLayer(acc, extractFirstFieldKey(exprSeq)))
      
      case (LinkExpression, exprSeq) +: tl => 
        jsonPathConstructor(tl, JsonLink(acc))
      
      case _ => throw new JsonPathException("Json path semantic error.", pathString)
    }
    
    def constructAccessPathLayer(child: JsonPath, key: String): JsonPath = child match {
      case JsonTerminus => JsonValueAccess(key, child)
      case JsonLink(_) => JsonValueAccess(key, child)
      case _ => JsonObjectAccess(key, child)
    }
    
    def extractFirstFieldKey(exprSeq: Seq[String]): String = {
      exprSeq.find { case IS_DELIMITER_REGEX(_*) => false case _ => true } match {
        case Some(key) => unescapeJsonKey(key)
        case None => throw new JsonPathException("No field found in expression sequence " + exprSeq.mkString, pathString)
      }
    }
    
    jsonPathConstructor(syntaxValidatorAndTransformer(pathString))
  }
  
  def validate(pathString: String): Boolean = {
    try {
      syntaxValidatorAndTransformer(pathString) match {
        case Nil => false;
        case _ => true;
      }
    } catch {
      case jpe: JsonPathException =>  false;
    }
  }
  
  /***
   * Returns a sequence of tuples, which contain the expression type and the corresponding sequence of strings in REVERSE order.
   * 
   * For example:
   * "one.two[three].four>.five"   
   * 
   *   transforms into -
   *
   *  Seq(
   *    (AccessExpression, Seq(".", "five"))
   *    (LinkExpression, Seq(">")),
   *    (AccessExpression, Seq(".", "four")),
   *    (AccessExpression, Seq("[", "three", "]")),
   *    (AccessExpression, Seq(".", "two")),
   *    (StartAccessExpression, Seq("one")),
   *  )
   */
  @throws(classOf[JsonPathException])
  private def syntaxValidatorAndTransformer(pathString: String): Seq[(ExpressionType, Seq[String])] = {
    

    /***
     * Tail recurses through the full expression sequence extracting expressions.
     * Returns a sequence of tuples, which contain the expression type and the corresponding sequence of strings.
     * This sequence is in reverse order, which allows for the AST to be build leaf up.
     * 
     * For example:
     *  Seq("one", ".", "two", "[", "three", "]" , ".", "four", ">", ".", "five")
     *   transforms into -
     *  
     *  Seq(
     *    (AccessExpression, Seq(".", "five"))
     *    (LinkExpression, Seq(">")),
     *    (AccessExpression, Seq(".", "four")),
     *    (AccessExpression, Seq("[", "three", "]")),
     *    (AccessExpression, Seq(".", "two")),
     *    (StartAccessExpression, Seq("one")),
     *  )
     */
    def transformToExpressions(exprSeq: Seq[String], acc: Seq[(ExpressionType, Seq[String])]): Seq[(ExpressionType, Seq[String])] = exprSeq match {
      case Nil => acc
      case seq => {
        val retTup = extractNextExpression(seq, testForExpressionType(AccessExpression, LinkExpression))
        transformToExpressions(retTup._2, retTup._1 +: acc)
      }
    }
    
    /***
     * Applies the expression test to all sub-sequences of the expression sequence that start at index 0
     * If the expression test returns an expression type, it is returned with the sub-sequence that matched it in a tuple.
     * This tuple is nested in another, the second element of which is the leftover sub-sequence.
     * 
     * If no sub-sequence passes the expression test an exception is thrown.
     */
    @throws(classOf[JsonPathException])
    def extractNextExpression(exprSeq: Seq[String], expressionTest: (Seq[String])=>Option[ExpressionType]): ((ExpressionType, Seq[String]), Seq[String]) = {
      for (i <- 1 to exprSeq.size) {
        exprSeq.splitAt(i) match {
          case (expr, remainder) => expressionTest(expr) match {
            case Some(exprType) => return ((exprType, expr), remainder)
            case None => 
          }
        }
      }
      throw new JsonPathException("String path of invalid format at " + exprSeq.mkString, pathString)
    }
    
    /***
     * Takes the expression types that should be tested for and constructs a lambda
     * that returns the expression type if a sequence of strings matches it.
     * 
     * Any none delimiters in the sequence of strings is replaces by a 'key' placeholder and then
     * concatenated to a single string.
     * This allows for easy matching and simple declaration of the expression patterns.
     */
    def testForExpressionType(exprTypes: ExpressionType*): Seq[String]=>Option[ExpressionType] = {
      (strSeq: Seq[String]) => {
        val exprStr = strSeq.map { s => s match {
          case IS_DELIMITER_REGEX(_*) => s
          case _ => exprTypes.head.keyStr
        }}.mkString
        exprTypes.find { x => x.patterns.exists { (p: Regex) => exprStr match {
          case p(_*) => true
          case _ => false
        }}}
      }
    }
    
    // Splits the pathString, separating delimiters from keys.
    // For example "one.two[three].four>.five" is split into
    // Seq("one", ".", "two", "[", "three", "]" , ".", "four", ">", ".", "five")
    val exprSeq = pathString.split(WITH_DELIMITER_REGEX_STR).toSeq
    
    //Pulls out the starting expression, usually just a key name ("one"), throws a JsonPathException
    //if the expression doesn't start properly.
    // In our example This tuple looks like:
    // ((StartAccessExpression, Seq("one")), Seq(".", "two", "[", "three", "]" , ".", "four", ">", ".", "five"))
    val startingTuple = extractNextExpression(exprSeq, testForExpressionType(StartAccessExpression))

    //Pulls out the remaining expressions tail recursively and returns them
    return transformToExpressions(startingTuple._2, Seq(startingTuple._1));
  }
}