package backend.auth

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import api._

object JWTOps {
  def generateToken(user: User): JWT.Token = user.name
  def userFromToken(token: JWT.Token): User = User(0, token)
  def isExpired(token: JWT.Token): Boolean = false
  def isValid(token: JWT.Token): Boolean = true
}
