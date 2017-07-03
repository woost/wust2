lazy val assets = ProjectRef(file("."), "assets")

def dockerNginx(tagPostfix: Option[String]) = Seq(
  dockerfile in docker := {
    val assetFolder = (WebKeys.assets in assets).value

    new Dockerfile {
      from("nginx:1.11.8-alpine")
      copy(baseDirectory(_ / ".." / "common").value, "/etc/nginx/conf.d/common")
      copy(baseDirectory(_ / "default.conf").value, "/etc/nginx/conf.d/default.conf")
      copy(assetFolder, "/public")
    }
  },

  imageNames in docker :=
    ImageName(namespace = Some("woost"), repository = "wust2.nginx", tag = tagPostfix) ::
    ImageName(namespace = Some("woost"), repository = "wust2.nginx", tag = Some(tagPostfix.map(_ + "-").getOrElse("") + version.value)) ::
    Nil
)

lazy val nginx = project.in(file("."))
  .aggregate(nginxHttps, nginxHttp)
lazy val nginxHttps = project.in(file("https"))
  .enablePlugins(DockerPlugin)
  .settings(dockerNginx(None))
lazy val nginxHttp = project.in(file("http"))
  .enablePlugins(DockerPlugin)
  .settings(dockerNginx(Some("http")))

