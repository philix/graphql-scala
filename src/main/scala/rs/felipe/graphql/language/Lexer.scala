package rs.felipe.graphql.language

import scala.collection.mutable

import rs.felipe.graphql.error.GraphQLError.syntaxError

object Lexer {
  type TokenKind = Int

  /**
   * A representation of a lexed Token. Value is optional, is it is
   * not needed for punctuators like BANG or PAREN_L.
   */
  case class Token(kind: TokenKind, start: Int, end: Int, value: Option[String] = None) {
    /**
     * A helper function to describe a token as a string for debugging
     */
    def getTokenDesc: String = value match {
      case Some(v) => getTokenKindDesc(kind) + " \"" + v + "\""
      case None => getTokenKindDesc(kind)
    }
  }

  type Lexer = Option[Int] => Token

  /**
   * Given a Source object, this returns a Lexer for that source.
   * A Lexer is a function that acts like a generator in that every time
   * it is called, it returns the next token in the Source. Assuming the
   * source lexes, the final Token emitted by the lexer will be of kind
   * EOF, after which the lexer will repeatedly return EOF tokens whenever
   * called.
   *
   * The argument to the lexer function is optional, and can be used to
   * rewind or fast forward the lexer to a new position in the source.
   */
  def lex(source: Source): Lexer = {
    var prevPosition = 0

    def nextToken(resetPosition: Option[Int]): Token = {
      val position = resetPosition match {
        case Some(pos) => pos
        case None => prevPosition
      }
      val token = readToken(source, position)
      prevPosition = token.end
      token
    }

    nextToken
  }

  /**
   * An enum describing the different kinds of tokens that the lexer emits.
   */
  object TokenKind {
    val EOF = 1
    val BANG = 2
    val DOLLAR = 3
    val PAREN_L = 4
    val PAREN_R = 5
    val SPREAD = 6
    val COLON = 7
    val EQUALS = 8
    val AT = 9
    val BRACKET_L = 10
    val BRACKET_R = 11
    val BRACE_L = 12
    val PIPE = 13
    val BRACE_R = 14
    val NAME = 15
    val VARIABLE = 16
    val INT = 17
    val FLOAT = 18
    val STRING = 19
  }

  /**
   * A helper array to describe a token kind as a string for debugging
   */
  val getTokenKindDesc = new Array[String](20)
  getTokenKindDesc(0) = ""
  getTokenKindDesc(TokenKind.EOF) = "EOF"
  getTokenKindDesc(TokenKind.BANG) = "!"
  getTokenKindDesc(TokenKind.DOLLAR) = "$"
  getTokenKindDesc(TokenKind.PAREN_L) = "("
  getTokenKindDesc(TokenKind.PAREN_R) = ")"
  getTokenKindDesc(TokenKind.SPREAD) = "..."
  getTokenKindDesc(TokenKind.COLON) = ":"
  getTokenKindDesc(TokenKind.EQUALS) = "="
  getTokenKindDesc(TokenKind.AT) = "@"
  getTokenKindDesc(TokenKind.BRACKET_L) = "["
  getTokenKindDesc(TokenKind.BRACKET_R) = "]"
  getTokenKindDesc(TokenKind.BRACE_L) = "{"
  getTokenKindDesc(TokenKind.PIPE) = "|"
  getTokenKindDesc(TokenKind.BRACE_R) = "}"
  getTokenKindDesc(TokenKind.NAME) = "Name"
  getTokenKindDesc(TokenKind.VARIABLE) = "Variable"
  getTokenKindDesc(TokenKind.INT) = "Int"
  getTokenKindDesc(TokenKind.FLOAT) = "Float"
  getTokenKindDesc(TokenKind.STRING) = "String"

  private def charCodeAt(seq: CharSequence, index: Int): Int = Character.codePointAt(seq, index)
  private def fromCharCode(codePoints: Int*): String = new String(codePoints.toArray, 0, codePoints.length)

  private def printCharCode(code: Int): String = {
    if (code < 0) {
      "<EOF>"
    } else {
      code match {
        case 34  => """\""""
        case 47  => """"\/""""
        case 92  => """"\\"""
        case 127 => """"\b""""
        case 10  => """"\n""""
        case 9   => """"\t""""
        case 12  => """"\f""""
        case 13  => """"\r""""
        case _ => {
          if (code >= 32 && code < 0x007F) {
            s""""${fromCharCode(code)}""""
          } else {
            val hex = "000" + Integer.toString(code, 16)
            val slice = hex.substring(hex.length - 4, hex.length).toUpperCase
            s""""\\u${slice}""""
          }
        }
      }
    }
  }

  /**
   * Gets the next token from the source starting at the given position.
   *
   * This skips over whitespace and comments until it finds the next lexable
   * token, then lexes punctuators immediately or calls the appropriate helper
   * function for more complicated tokens.
   */
  def readToken(source: Source, fromPosition: Int): Token = {
    val body = source.body
    val bodyLength = body.length

    val position = positionAfterWhitespace(body, fromPosition)
    if (position >= bodyLength) {
      return Token(TokenKind.EOF, position, position)
    }

    val code = charCodeAt(body, position)

    // SourceCharacter
    if (code < 0x0020 && code != 0x0009 && code != 0x000A && code != 0x000D) {
      throw syntaxError(
        source,
        position,
        s"""Invalid character ${printCharCode(code)}.""")
    }

    def throwError: Token =
      throw syntaxError(source, position,  s"""Unexpected character ${printCharCode(code)}.""")

    code match {
      // !
      case 33 => Token(TokenKind.BANG, position, position + 1)
      // $
      case 36 => Token(TokenKind.DOLLAR, position, position + 1)
      // (
      case 40 => Token(TokenKind.PAREN_L, position, position + 1)
      // )
      case 41 => Token(TokenKind.PAREN_R, position, position + 1)
      // .
      case 46 =>
        if (position + 2 >= bodyLength) {
          throw syntaxError(source, bodyLength, "Unexpected character \"EOF\".")
        } else {
          if (charCodeAt(body, position + 1) == 46 && charCodeAt(body, position + 2) == 46) {
            Token(TokenKind.SPREAD, position, position + 3)
          } else {
            throwError
          }
        }
      // :
      case 58 => Token(TokenKind.COLON, position, position + 1)
      // =
      case 61 => Token(TokenKind.EQUALS, position, position + 1)
      // @
      case 64 => Token(TokenKind.AT, position, position + 1)
      // [
      case 91 => Token(TokenKind.BRACKET_L, position, position + 1)
      // ]
      case 93 => Token(TokenKind.BRACKET_R, position, position + 1)
      // {
      case 123 => Token(TokenKind.BRACE_L, position, position + 1)
      // |
      case 124 => Token(TokenKind.PIPE, position, position + 1)
      // }
      case 125 => Token(TokenKind.BRACE_R, position, position + 1)
      // A-Z
      case (65 | 66 | 67 | 68 | 69 | 70 | 71 | 72 | 73 | 74 | 75 | 76 | 77 | 78
           | 79 | 80 | 81 | 82 | 83 | 84 | 85 | 86 | 87 | 88 | 89 | 90) => readName(source, position)
      // _
      case 95 => readName(source, position)
      // a-z
      case (97 | 98 | 99 | 100 | 101 | 102 | 103 | 104 | 105 | 106 | 107 | 108 | 109
           | 110 | 111 | 112 | 113 | 114 | 115 | 116 | 117 | 118 | 119 | 120 | 121 | 122)
              => readName(source, position)
      // -
      case 45 => readNumber(source, position, code)
      // 0-9
      case (48 | 49 | 50 | 51 | 52 | 53 | 54 | 55 | 56 | 57) => readNumber(source, position, code)
      // "
      case 34 => readString(source, position)
      case _ => throwError
    }
  }

  /**
   * Reads from body starting at startPosition until it finds a character that
   * doesn't satisfy the predicate, then returns the position of that character
   * for lexing.
   */
  private def positionAfterPredicate(body: String, pred: Int => Boolean, startPosition: Int): Int = {
    val bodyLength = body.length

    def aux(position: Int): Int = {
      if (position < bodyLength) {
        val code = charCodeAt(body, position)
        if (pred(code)) {
          return aux(position + 1)
        }
      }

      position
    }

    aux(startPosition)
  }

  /**
   * Reads from body starting at startPosition until it finds a non-whitespace
   * or commented character, then returns the position of that character for
   * lexing.
   */
  private def positionAfterWhitespace(body: String, startPosition: Int): Int = {
    val bodyLength = body.length

    def isWhitespace(code: Int) =
      // BOM
      code == 0xFEFF ||
      // White Space
      code == 0x0009 || // tab
      code == 0x0020 || // space
      // Line Terminator
      code == 0x000A || // new line
      code == 0x000D || // carriage return
      // Comma
      code == 0x002C

    def isNotEndOfComment(code: Int) =
      // SourceCharacter but not LineTerminator
      (code > 0x001F || code == 0x0009) && code != 0x000A && code != 0x000D

    def aux(startPosition: Int): Int = {
      if (startPosition < bodyLength) {
        val position = positionAfterPredicate(body, isWhitespace, startPosition)
        if (position < bodyLength) {
          val code = charCodeAt(body, position)
          if (code == 35) { // #
            return aux(positionAfterPredicate(body, isNotEndOfComment, position + 1))
          }
        }
        return position
      }

      startPosition
    }

    aux(startPosition)
  }

  /**
   * Reads a number token from the source file, either a float
   * or an int depending on whether a decimal point appears.
   *
   * Int:   -?(0|[1-9][0-9]*)
   * Float: -?(0|[1-9][0-9]*)(\.[0-9]+)?((E|e)(+|-)?[0-9]+)?
   */
  def readNumber(source: Source, start: Int, firstCode: Int): Token = {
    var code = firstCode
    val body = source.body
    var position = start
    var isFloat = false

    def safeCharCodeAtPosition(pos: Int): Int =
      if (pos >= body.length) -1 else charCodeAt(body, pos)

    def consumeCharCode: Int = {
      position += 1
      safeCharCodeAtPosition(position)
    }

    if (code == 45) { // -
      code = consumeCharCode
    }

    if (code == 48) { // 0
      code = consumeCharCode
      if (code >= 48 && code <= 57) {
        throw syntaxError(
          source,
          position,
          s"""Invalid number, unexpected digit after 0: ${printCharCode(code)}.""")
      }
    } else {
      position = readDigits(source, position, code)
      code = safeCharCodeAtPosition(position)
    }

    if (code == 46) { // .
      isFloat = true

      code = consumeCharCode
      position = readDigits(source, position, code)
      code = safeCharCodeAtPosition(position)
    }

    if (code == 69 || code == 101) { // E e
      isFloat = true

      code = consumeCharCode
      if (code == 43 || code == 45) { // + -
        code = consumeCharCode
      }
      position = readDigits(source, position, code)
    }

    Token(
      if (isFloat) TokenKind.FLOAT else TokenKind.INT,
      start,
      position,
      Some(body.substring(start, position))
    )
  }

  /**
   * Returns the new position in the source after reading digits.
   */
  def readDigits(source: Source, start: Int, firstCode: Int): Int = {
    val body = source.body
    var position = start
    var code = firstCode

    if (code >= 48 && code <= 57) { // 0 - 9
      do {
        position += 1
        code = if (position >= body.length) 0 else charCodeAt(body, position)
      } while (code >= 48 && code <= 57) // 0 - 9
      position
    } else {
      throw syntaxError(
        source,
        position,
        s"Invalid number, expected digit but got: ${printCharCode(code)}.")
    }
  }

  /**
   * Reads a string token from the source file.
   *
   * "([^"\\\u000A\u000D\u2028\u2029]|(\\(u[0-9a-fA-F]{4}|["\\/bfnrt])))*"
   */
  def readString(source: Source, start: Int): Token = {
    val body = source.body
    var position = start + 1
    var chunkStart = position
    var code = 0
    var value = new mutable.StringBuilder

    def safeCharCodeAtPosition(pos: Int): Int =
      if (pos >= body.length) {
        throw syntaxError(source, body.length, "Unexpected character \"EOF\".")
      } else {
        charCodeAt(body, pos)
      }

    def shouldContinue: Boolean = {
      if (position < body.length) {
        code = charCodeAt(body, position)
        // not LineTerminator
        code != 0x000A && code != 0x000D &&
        // not Quote (")
        code != 34
      } else {
        false
      }
    }

    while (shouldContinue) {
      // SourceCharacter
      if (code < 0x0020 && code != 0x0009) {
        throw syntaxError(
          source,
          position,
          s"""Invalid character within String: ${printCharCode(code)}."""
        )
      }

      position += 1;
      if (code == 92) { // \
        value ++= body.substring(chunkStart, position - 1)
        code = safeCharCodeAtPosition(position)
        code match {
          case 34 => value += '"'
          case 47 => value += '/'
          case 92 => value += '\\'
          case 98 => value += '\b'
          case 102 => value += '\f'
          case 110 => value += '\n'
          case 114 => value += '\r'
          case 116 => value += '\t'
          case 117 => { // u
            val charCode = uniCharCode(
              safeCharCodeAtPosition(position + 1).toByte,
              safeCharCodeAtPosition(position + 2).toByte,
              safeCharCodeAtPosition(position + 3).toByte,
              safeCharCodeAtPosition(position + 4).toByte
            )
            if (charCode < 0) {
              val slice = body.substring(position + 1, position + 5)
              throw syntaxError(
                source,
                position,
                s"""Invalid character escape sequence: \\u${slice}.""")
            }
            value += fromCharCode(charCode).charAt(0)
            position += 4
          }
          case _ => throw syntaxError(
            source,
            position,
            s"""Invalid character escape sequence: \\${fromCharCode(code)}.""")
        }
        position += 1
        chunkStart = position
      }
    }

    if (code != 34) { // quote (")
      throw syntaxError(source, position, "Unterminated string.")
    }

    value.append(body.substring(chunkStart, position))
    Token(TokenKind.STRING, start, position + 1, Some(value.toString))
  }

  /**
   * Converts four hexidecimal chars to the integer that the
   * string represents. For example, uniCharCode('0','0','0','f')
   * will return 15, and uniCharCode('0','0','f','f') returns 255.
   *
   * Returns a negative number on error, if a char was invalid.
   *
   * This is implemented by noting that char2hex() returns -1 on error,
   * which means the result of ORing the char2hex() will also be negative.
   */
  private def uniCharCode(a: Byte, b: Byte, c: Byte, d: Byte): Int =
    char2hex(a) << 12 | char2hex(b) << 8 | char2hex(c) << 4 | char2hex(d)

  /**
   * Converts a hex character to its integer value.
   * '0' becomes 0, '9' becomes 9
   * 'A' becomes 10, 'F' becomes 15
   * 'a' becomes 10, 'f' becomes 15
   *
   * Returns -1 on error.
   */
  private def char2hex(a: Int): Int =
    if (a >= 48 && a <= 57) {  // 0-9
      a - 48
    } else if (a >= 65 && a <= 70) {  // A-F
      a - 55
    } else if (a >= 97 && a <= 102) {  // a-f
      a - 87
    } else {
      -1
    }

  /**
   * Reads an alphanumeric + underscore name from the source.
   *
   * [_A-Za-z][_0-9A-Za-z]*
   */
  def readName(source: Source, position: Int): Token = {
    val body = source.body

    def isIdentifierChar(code: Int) =
      code == 95 || // _
      code >= 48 && code <= 57 || // 0-9
      code >= 65 && code <= 90 || // A-Z
      code >= 97 && code <= 122 // a-z

    val end = positionAfterPredicate(body, isIdentifierChar, position + 1)

    Token(
      TokenKind.NAME,
      position,
      end,
      Some(body.substring(position, end))
    )
  }
}
