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
* HOST_DOMAIN: domain (example.com)
* POSTGRES_PASSWORD: a password for the postgres application user 'wust'
* CERT_DIR: directory on docker host containing SSL_CERT and SSL_KEY, is mounted read-only in nginx container
* SSL_CERT: path to tls certificate (relative to CERT_DIR), used in [ssl_certificate](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_certificate) 
* SSL_KEY: path to private tls key (relative to CERT_DIR), used in [ssl_certificate_key](https://nginx.org/en/docs/http/ngx_http_ssl_module.html#ssl_certificate_key)

Start production in docker:
```
$ ./start prod
```

Or without tls:
```
$ ./start prod.http
```
