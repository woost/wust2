package wust.core

import com.roundeights.hasher.Hasher
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import wust.api.{Authentication, Password}
import wust.core.auth.{JWT, OAuthClientServiceLookup}
import wust.core.config.ServerConfig
import wust.core.pushnotifications.PushClients
import wust.db.{Data, Db}
import DbConversions._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class PasswordResetEndpoint(db: Db, jwt: JWT, config: ServerConfig) {
  import akka.http.scaladsl.server.Directives._

  private val linkUrl = s"https://${config.host}/#view=login"
  private def link =  s"""<a href="$linkUrl">Go back to app</a>"""
  private def redirectSuccessMessage = redirect(Uri(linkUrl), StatusCodes.TemporaryRedirect)
  private def invalidMessage = complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"Cannot reset password. This token was already used or is invalid or expired. $link"))
  private def errorMessage = complete(StatusCodes.InternalServerError -> s"Sorry, we cannot reset your password right now. Please try again later.")

  private def passwordDigest(password: Password) = Hasher(password.string).bcrypt

  def form(token: Authentication.Token)(implicit ec: ExecutionContext, materializer: ActorMaterializer): Route = {
    scribe.info("Getting form for password reset")
    jwt.passwordResetUserFromToken(token) match {
      case Some(user) =>
        onComplete(db.user.checkIfEqualUserExists(user)) {
          case Success(Some(_)) =>
            scribe.info(s"Serving password reset form for user ${user.id}")
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, passwordForm))
          case Success(None) => invalidMessage
          case Failure(t) =>
            scribe.error(s"Failed to server reset password form", t)
            errorMessage
        }
      case _ => invalidMessage
    }
  }

  def reset(token: Authentication.Token, newPassword: Password)(implicit ec: ExecutionContext, materializer: ActorMaterializer): Route = {
    scribe.info("Setting a new password")
    jwt.passwordResetUserFromToken(token) match {
      case Some(user) =>
        // we first check if the user from the token really matches the current user in the db (including the revision).
        // thereby we assure that an old token cannot be used after the password was changed.
        onComplete(db.ctx.transaction { implicit ec =>
          db.user.checkIfEqualUserExists(user).flatMap {
            case Some(_) =>
              scribe.info(s"Successfully resetted password for user ${user.id}")
              db.user.changePassword(user.id, passwordDigest(newPassword)).map(_ => true)
            case None => Future.successful(false)
          }
        }) {
          case Success(true) => redirectSuccessMessage
          case Success(false) => invalidMessage
          case Failure(t) =>
            scribe.error(s"Failed to reset password", t)
            errorMessage
        }
      case _ => invalidMessage
    }
  }

  private def passwordForm = s"""
<html>
<head>
  $formStyle
</head>
<body>
<div class="form-style">
<div class="form-style-heading">Woost - Reset your Password</div>
<form action="" method="post">
<label for="password"><span>New Password</span><input type="password" class="input-field" name="password" value="" required /></label>
<label><span> </span><input type="submit" value="Submit" /></label>
</form>
</div>
</body>
  """

  private val formStyle = """
<style type="text/css">
.form-style{
	max-width: 500px;
	padding: 20px 12px 10px 20px;
	font: 13px Arial, Helvetica, sans-serif;
}
.form-style-heading{
	font-weight: bold;
	font-style: italic;
	border-bottom: 2px solid #ddd;
	margin-bottom: 20px;
	font-size: 15px;
	padding-bottom: 3px;
}
.form-style label{
	display: block;
	margin: 0px 0px 15px 0px;
}
.form-style label > span{
	width: 100px;
	font-weight: bold;
	float: left;
	padding-top: 8px;
	padding-right: 5px;
}
.form-style span.required{
	color:red;
}
.form-style .tel-number-field{
	width: 40px;
	text-align: center;
}
.form-style input.input-field, .form-style .select-field{
	width: 48%;	
}
.form-style input.input-field,
.form-style .tel-number-field,
.form-style .textarea-field,
 .form-style .select-field{
	box-sizing: border-box;
	-webkit-box-sizing: border-box;
	-moz-box-sizing: border-box;
	border: 1px solid #C2C2C2;
	box-shadow: 1px 1px 4px #EBEBEB;
	-moz-box-shadow: 1px 1px 4px #EBEBEB;
	-webkit-box-shadow: 1px 1px 4px #EBEBEB;
	border-radius: 3px;
	-webkit-border-radius: 3px;
	-moz-border-radius: 3px;
	padding: 7px;
	outline: none;
}
.form-style .input-field:focus,
.form-style .tel-number-field:focus,
.form-style .textarea-field:focus,
.form-style .select-field:focus{
	border: 1px solid #0C0;
}
.form-style .textarea-field{
	height:100px;
	width: 55%;
}
.form-style input[type=submit],
.form-style input[type=button]{
  cursor: pointer;
	border: none;
	padding: 8px 15px 8px 15px;
	background: #2185D0;
	color: #fff;
	box-shadow: 1px 1px 4px #DADADA;
	-moz-box-shadow: 1px 1px 4px #DADADA;
	-webkit-box-shadow: 1px 1px 4px #DADADA;
	border-radius: 3px;
	-webkit-border-radius: 3px;
	-moz-border-radius: 3px;
}
.form-style input[type=submit]:hover,
.form-style input[type=button]:hover{
	background: #1678C2;
	color: #fff;
}
</style>
"""
}
