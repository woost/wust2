enablePlugins(DockerPlugin)

lazy val assets = ProjectRef(file("."), "assets")

dockerfile in docker := {
    val assetFolder = (WebKeys.assets in assets).value

    new Dockerfile {
        from("nginx:1.13.5-alpine")
        copy(baseDirectory(_ / "default.conf").value, "/etc/nginx/conf.d/default.conf")
        copy(assetFolder, "/public")
    }
}

imageNames in docker :=
  ImageName(namespace = Some("woost"), repository = "wust2.nginx") ::
  ImageName(namespace = Some("woost"), repository = "wust2.nginx", tag = Some(version.value)) ::
  Nil
