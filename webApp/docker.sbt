enablePlugins(DockerPlugin)

lazy val assets = ProjectRef(file("."), "assets")

dockerfile in docker := {
    val assetFolder = (WebKeys.assets in assets).value

    new Dockerfile {
        from("nginx:1.13.9-alpine")
        copy(baseDirectory(_ / "docker" / "default.conf").value, "/etc/nginx/conf.d/default.conf")
        copy(assetFolder, "/public")
    }
}

imageNames in docker :=
  ImageName(namespace = Some("woost"), repository = "web") ::
  ImageName(namespace = Some("woost"), repository = "web", tag = Some(version.value)) ::
  Nil
