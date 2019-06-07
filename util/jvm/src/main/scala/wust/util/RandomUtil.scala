package wust.util

object RandomUtil {
  import java.math.BigInteger
  import java.security.SecureRandom

  private val random = new SecureRandom()

  def alphanumeric(nrChars: Int = 24): String = {
    new BigInteger(nrChars * 5, random).toString(32)
  }
}
