enablePlugins(DockerPlugin)

dockerfile in docker := Docker.java(assembly.value)
imageNames in docker := Docker.imageNames("slack").value
