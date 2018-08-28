enablePlugins(DockerPlugin)

dockerfile in docker := Docker.java(assembly.value, healthCheckUrl = Some("localhost:8080/health"))
imageNames in docker := Docker.imageNames("core").value
