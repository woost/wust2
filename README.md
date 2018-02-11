# Woost
[![Build Status](https://travis-ci.org/woost/wust2.svg?branch=master)](https://travis-ci.org/woost/wust2)
[![Coverage Status](https://coveralls.io/repos/github/woost/wust2/badge.svg)](https://coveralls.io/github/woost/wust2)
[![Join the chat at https://gitter.im/wust2/Lobby](https://badges.gitter.im/wust2/Lobby.svg)](https://gitter.im/wust2/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Goal: Scale communication and collaboration within large groups.
The core idea can be imagined as a mix of the expressiveness of mind-mapping, Wiki and realtime collaborative editing.

This project is in an early stage of development. You can already play around with the online version: https://wust.space. Current master is deployed automatically to https://wust.space:8443.

Contributions very welcome. Please ask questions and share ideas.

# Rough Architecture
A directed graph stored in postgres accessed via rpc-calls over websockets and binary serialization, visualized using reactive programming and a force-directed graph layout.

# Building blocks
* [scala](https://github.com/scala/scala)/[scala-js](https://github.com/scala-js/scala-js) (scala for backend, scala-js is scala compiled to javascript for the webApp. Allows to share code between both.)
* [nginx](https://github.com/nginx/nginx) (serving static assets, https, forwarding websocket connections to backend)
* [postgres](https://github.com/postgres/postgres) (relational database with views and stored procedures for graph traversal)
* [flyway](https://github.com/flyway/flyway) (database migrations)
* [quill](https://github.com/getquill/quill) (compile-time language integrated database queries for Scala)
* [akka](https://github.com/akka/akka) (message passing in backend, websocket server)
* [sloth](https://github.com/cornerman/sloth) (type safe rpc calls for implementing webApp-to-backend api)
* [mycelium](https://github.com/cornerman/mycelium) (request, response and events over websockets)
* [boopickle](https://github.com/suzaku-io/boopickle) (fast and boilerplate-free binary serialization, similar to protobuf. Used for webApp/backend communication)
* [outwatch](https://github.com/outwatch/outwatch) (UI-library for reactive programming bindings to DOM-Nodes)
* [d3](https://github.com/d3/d3) (visualization library, used for graph visualization and drag&drop)

# Development
Requirements:
* sbt
* docker, docker-compose
* node, yarn
* phantomjs
* gcc and make

_Note:_ `gcc/make` is not a direct requierement of this project, but some `npm` packages requiere a C compiler. You will most probably notice that if you get a runtime exception from `npm`.

Starting all needed services in docker (e.g. postgres with initialization) and run sbt with corresponding environment variables:
```
$ ./start sbt
```

In the sbt prompt, you can then start watching sources and recompile while developing:
```
> dev
```

If you are only developing the webApp, you can also skip recompilation of the backend:
```
> devf
```

Access wust via http://localhost:12345

The start script is the central script for developers.
From here, you can also run db migrations, access psql, run tests or start a production stack with test settings:
```
start [ sbt, migrate, psql <options>, pgdump, pgrestore <file>, pgclean, prod, prod.http, prod.slack, test, test.postgres, test.integration ]
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

All used docker services are defined in `docker/services.yml` and can be configured with the following environment variables:
* **POSTGRES_PASSWORD**: a password for the postgres application user 'wust'
* **WUST_AUTH_SECRET**: a secret for signing JWT tokens
* **WUST_EMAIL_ADDRESS**: from address for sent email (optional)
* **WUST_SMTP_ENDPOINT**: smtp endpoint (optional)
* **WUST_SMTP_USER**: smtp username (optional)
* **WUST_SMTP_PASS**: smtp password (optional)

The compose stack `docker/compose-prod.yml` is an example how to run wust in docker. Start the whole stack with docker-compose:
```
$ docker-compose --file docker/compose-prod.yml up
```
