# Wust2
[![Build Status](https://travis-ci.org/woost/wust2.svg?branch=master)](https://travis-ci.org/woost/wust2)
[![Join the chat at https://gitter.im/wust2/Lobby](https://badges.gitter.im/wust2/Lobby.svg)](https://gitter.im/wust2/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# development

Requirements:
* sbt
* docker

Start all needed services in docker (e.g. postgres with initialization) and run sbt with corresponding environment variables:
```
$ ./start dev
```

In the sbt prompt, you can then start watching sources and recompile while developing:
```
> dev
```

Access wust via http://localhost:12345/workbench/index.html

# build docker images

Build all docker images in project:
```
$ sbt docker
```

# production

Set environment variables according to your setup:
```sh
HOST_DOMAIN=<domain>
POSTGRES_USER=<a user name>
POSTGRES_PASSWORD=<a password>
NGINX_VOLUME=<readonly host volume for nginx>
SSL_CERT=<tls certificate file path (relative to NGINX_VOLUME)>
SSL_KEY=<tls private key file path (relative to NGINX_VOLUME)>
```

Start production in docker:
```
$ ./start prod
```

Or without tls:
```
$ ./start prod.http
```
