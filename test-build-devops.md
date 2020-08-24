# Build application and DevOps

## Run the unit tests
```
sbt test
```

## Run integration test
It makes use of the database.  Start database before running the test.
```
docker run -dit --name=postgres-service-in-fp-scala -p 5432:5432 -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=postgres postgres
```

```
sbt "it:test"
```


## Startup service for testing
Make sure the database is.  Start the REST service by 
```
sbt run
```

## Dockerize the application
When it is ready to build the docker image.
* In `plugins.sbt`, add the sbt plugin `addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.4")`
* In `build.sbt`, enable `JavaAppPackaging` and `DockerPlugin` plugins.  The setup is as follows:
```
enablePlugins(JavaAppPackaging)

// building a docker image
enablePlugins(DockerPlugin)

import com.typesafe.sbt.packager.docker._

version in Docker := version.value.trim
dockerUpdateLatest in Docker := true
packageName in Docker := s"jinilover/${name.value}"
dockerBuildOptions ++= List("-t", dockerAlias.value.versioned)

val entryScript = "docker-entrypoint.sh"

dockerCommands := Seq(
  Cmd("FROM", "openjdk:11.0.4-jre-slim"),
  Cmd("MAINTAINER", "jinilover <columbawong@gmail.com>"),
  Cmd("WORKDIR", "/opt"),
  Cmd("LABEL", "version=\"" + version.value.trim + "\""),
  Cmd("ENV", "APPLICATION_VERSION", version.value.trim),
  Cmd("ENV", "APPLICATION_NAME", name.value),
  Cmd("ADD", "./opt/docker", "/opt"),
  Cmd("EXPOSE", "9000 9000"),
  Cmd("RUN", s"mv /opt/${entryScript} /opt/bin/${entryScript}"),
  Cmd("RUN", s"chmod +x /opt/bin/${entryScript}"),
  Cmd("CMD", s"/opt/bin/${entryScript} /opt/bin/${name.value}")
)
```
* `docker-entrypoint.sh` is required to run the REST service when the container is up.  Therefore this script should be available under `src/universal`
* Run `sbt docker:publishLocal` to build the image.
* Type `docker images`.  The image should be available at the local repository.
* Prepare `docker-compose.yml` to set up the containers of postgres db and the REST service.
* Run `docker-compose up -d`
* Test it by typing http://localhost:9000/

## Reference
* For more information about the sbt docker plugin, please refer to https://www.scala-sbt.org/sbt-native-packager/formats/docker.html