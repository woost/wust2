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

If you are only developing the frontend, you can also skip recompilation of the backend:
```
> devf
```

Access wust via http://localhost:12345/workbench/index.html

# build docker images

Build all docker images in project:
```
$ sbt docker
```

# production

Set environment variables according to your setup:
* **HOST_DOMAIN**: domain (example.com)
* **POSTGRES_PASSWORD**: a password for the postgres application user 'wust'
* **CERT_DIR**: directory on docker host containing SSL_CERT and SSL_KEY, is mounted read-only in nginx container
* **SSL_CERT**: path to tls certificate (relative to CERT_DIR), used in [ssl_certificate](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_certificate) 
* **SSL_KEY**: path to private tls key (relative to CERT_DIR), used in [ssl_certificate_key](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_certificate_key)

See also [How to create a self-signed certificate](https://stackoverflow.com/questions/10175812/how-to-create-a-self-signed-certificate-with-openssl)

For persisting its data, the postgres container mounts the folder `/pg_data` on the docker host.

Start production in docker:
```
$ ./start prod
```

Or without tls:
```
$ ./start prod.http
```

Example:
```bash
HOST_DOMAIN=yourdomain.com POSTGRES_PASSWORD=password CERT_DIR=/home/user/certs SSL_CERT=cert.pem SSL_KEY=key.pem ./start prod
```

# building blocks

* [scala](https://github.com/scala/scala)/[scala-js](https://github.com/scala-js/scala-js)
* [nginx](https://github.com/nginx/nginx)
* [postgres](https://github.com/postgres/postgres)
* [flyway](https://github.com/flyway/flyway)
* [quill](https://github.com/getquill/quill)
* [akka](https://github.com/akka/akka)
* [autowire](https://github.com/lihaoyi/autowire)
* [boopickle](https://github.com/suzaku-io/boopickle)
* [monadic-html](https://github.com/OlivierBlanvillain/monadic-html)
* [d3](https://github.com/d3/d3)
