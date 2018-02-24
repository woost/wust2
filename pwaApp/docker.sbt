enablePlugins(DockerPlugin)

dockerfile in docker := {
    val assetFolder = {
        (webpack in (Compile, fullOptJS)).value
        (crossTarget in (Compile, fullOptJS)).value / "dist"
    }

    new Dockerfile {
        from("nginx:1.13.9-alpine")
        copy(baseDirectory(_ / "nginx" / "default.conf").value, "/etc/nginx/conf.d/default.conf")
        copy(assetFolder, "/public/assets")
    }
}

imageNames in docker := Defs.dockerVersionTags.value.map { v =>
  ImageName(namespace = Some("woost"), repository = "pwa", tag = Some(v))
}
