package wust.ids

object CuidOrdering {
  def calculate(nodeId: Cuid): BigDecimal = {
    val parts = nodeId.toParts
    import parts._

    def numberDigitsCuid8 = 13
    def numberDigitsCuid4 = 7

    BigDecimal(timestamp) +
      (BigDecimal(fingerprint) / BigDecimal(10).pow(numberDigitsCuid4)) +
      (BigDecimal(counter) / BigDecimal(10).pow(numberDigitsCuid4 * 2) +
        (BigDecimal(random) / BigDecimal(10).pow(numberDigitsCuid4 * 2 + numberDigitsCuid8)))
  }
}
