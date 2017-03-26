# antlion-clojure

[![CircleCI](https://circleci.com/gh/boxp/antlion-clojure.svg?style=svg)](https://circleci.com/gh/boxp/antlion-clojure)

A Slack bot designed to play code escape game.

## Prerequirements

- java(http://www.oracle.com/technetwork/java/javase/downloads/index-jsp-138363.html)
- redis(https://redis.io/)
- leiningen(http://leiningen.org/)

## Usage

1. Create the .lein-env file.

```clj:.lein-env
{:antlion-clojure-token "xoxb-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
 :antlion-clojure-invite-token "xoxp-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
 :redis-master-port "tcp://127.0.0.1:6379"
 :redis-slave-port "tcp://127.0.0.2:6379"
 :antlion-clojure-master-user-name "horai"}
```

2. Prepare redis for the local environment.

## linux

```bash
	systemctl start redis
```

## mac

```bash
	redis-server /usr/local/etc/redis.conf
```

3. After that, we only need to do it!

```bash
	lein run
```

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
