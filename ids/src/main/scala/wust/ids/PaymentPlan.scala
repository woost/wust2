package wust.ids

sealed trait PaymentPlan
object PaymentPlan {
  final case object Free extends PaymentPlan
  final case object Business extends PaymentPlan
}
