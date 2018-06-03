enablePlugins(DockerPlugin)

dockerfile in docker := {
    val webpackFolder = {
        (webpack in (Compile, fullOptJS)).value
        (crossTarget in (Compile, fullOptJS)).value
    }
    val assetFolder = webpackFolder / "dist"

    new Dockerfile {
        from(Deps.docker.nginx)
        copy(baseDirectory(_ / "nginx" / "default.conf").value, "/etc/nginx/conf.d/default.conf")
        copy(assetFolder, "/public")
    }
}

imageNames in docker := Defs.dockerVersionTags.value.map { v =>
  ImageName(namespace = Some("woost"), repository = "web", tag = Some(v))
}
