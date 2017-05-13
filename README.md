# Wust2
[![Build Status](https://travis-ci.org/woost/wust2.svg?branch=master)](https://travis-ci.org/woost/wust2)
[![Coverage Status](https://coveralls.io/repos/github/woost/wust2/badge.svg)](https://coveralls.io/github/woost/wust2)
[![Join the chat at https://gitter.im/wust2/Lobby](https://badges.gitter.im/wust2/Lobby.svg)](https://gitter.im/wust2/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Goal: Scale communication and collaboration within large groups.
The core idea can be imagined as a mix of the expressiveness of mind-mapping, Wiki and realtime collaborative editing.

This project is in an early stage of development. You can already play around with the online version: https://wust.space

Contributions very welcome. Please ask questions and share ideas.

# Rough Architecture
A hypergraph stored in postgres accessed via rpc-calls over websockets and binary serialization, visualized using reactive programming and a force-directed graph layout.

# Building blocks
* [scala](https://github.com/scala/scala)/[scala-js](https://github.com/scala-js/scala-js) (scala for backend, scala-js is scala compiled to javascript for the frontend. Allows to share code between both.)
* [nginx](https://github.com/nginx/nginx) (serving static assets, https, forwarding websocket connections to backend)
* [postgres](https://github.com/postgres/postgres) (relational database with views and stored procedures for graph traversal)
* [flyway](https://github.com/flyway/flyway) (database migrations)
* [quill](https://github.com/getquill/quill) (compile-time language integrated database queries for Scala)
* [akka](https://github.com/akka/akka) (message passing in backend, websocket server)
* [autowire](https://github.com/lihaoyi/autowire) (type safe rpc calls for implementing frontend-to-backend api)
* [boopickle](https://github.com/suzaku-io/boopickle) (fast and boilerplate-free binary serialization, similar to protobuf. Used for frontend/backend communication)
* [scalatags-rx](https://github.com/rtimush/scalatags-rx) (UI-library for reactive programming bindings to DOM-Nodes)
* [d3](https://github.com/d3/d3) (visualization library, used for graph visualization and drag&drop)

# Development
Requirements:
* sbt
* docker, docker-compose
* node, yarn
* phantomjs
* [fork of scala.rx](https://github.com/fdietze/scala.rx): `$ sbt ++2.11.8 scalarxJS/publish-local`

Starting all needed services in docker (e.g. postgres with initialization) and run sbt with corresponding environment variables:
```
$ ./start dev
```

In the sbt prompt, you can then start watching sources and recompile while developing:
```
> dev
```

If you are only developing the frontend, you can also skip recompilation of the backend:
```
> devf
```

Access wust via http://localhost:12345/workbench/index.html

The start script is the central script for developers.
From here, you can also run db migrations, access psql, run tests or start a production stack with test settings:
```
start [psql,dev,migrate,prod.http,prod,test.postgres,test.integration,test.prod.http,test.prod]
```


# Docker images
Build all docker images in project:
```
$ sbt docker
```

The images are automatically published to [dockerhub](https://hub.docker.com/r/woost/) in each travis build on `master`.

# Deployment
Requirements:
* docker
* docker-compose

Set environment variables according to your setup:
* **POSTGRES_PASSWORD**: a password for the postgres application user 'wust'
* **WUST_AUTH_SECRET**: a secret for signing JWT tokens
* **WUST_EMAIL_ADDRESS**: from address for sent email (optional)
* **WUST_SMTP_ENDPOINT**: smtp endpoint (optional)
* **WUST_SMTP_USER**: smtp username (optional)
* **WUST_SMTP_PASS**: smtp password (optional)
* **CERT_DIR**: directory on docker host containing fullchain.pem ([ssl_certificate](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_certificate)) and privkey.pem ([ssl_certificate_key](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_certificate_key)), is mounted read-only in nginx container

See also [How to create a self-signed certificate](https://stackoverflow.com/questions/10175812/how-to-create-a-self-signed-certificate-with-openssl)

For persisting its data, the postgres container mounts the folder `./pg_data` from the docker host.

Start production in docker:
```
$ docker-compose --file docker/compose-prod.yml up nginx
```

Or without tls:
```
$ docker-compose --file docker/compose-prod.yml up nginx-http
```

Example:
```bash
POSTGRES_PASSWORD=password WUST_AUTH_SECRET=secret CERT_DIR=/home/user/certs docker-compose --file docker/compose-prod.yml up nginx
```

Please adapt the compose file to your needs, before deploying to production.
