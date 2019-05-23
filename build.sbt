ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / organization := "com.callhandling"

lazy val v = new {
  val akka = "2.5.22"
  val akkaTyped = "2.5.8"
  val scalatest = "3.0.7"
  val junit = "4.12"
  val cassandraPlugin = "0.96"
}

lazy val `media-file-encoder` = project.in(file("."))
  .aggregate(`media-manager-service`, `media-manager-state`, `media-manager-app`)
  .enablePlugins(JavaAppPackaging)

lazy val `media-manager-service` = project
  .dependsOn(`media-manager-state`)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test
    )
  )

lazy val `media-manager-state` = project
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test,
      "junit" % "junit" % v.junit % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % v.akka % Test,

      "com.typesafe.akka" %% "akka-actor" % v.akka,
      "com.typesafe.akka" %% "akka-actor-typed" % v.akka,
//      "com.typesafe.akka" %% "akka-stream" % v.akka,
      "com.typesafe.akka" %% "akka-stream-typed" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-typed" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % v.akka,
      "com.typesafe.akka" %% "akka-persistence-typed" % v.akka,
      "com.typesafe.akka" %% "akka-persistence-query" % v.akka,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % v.cassandraPlugin,
      // this allows us to start cassandra from the sample
      "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % v.cassandraPlugin,

      "com.typesafe.akka" %% "akka-http" % "10.1.8",
//      "com.typesafe.akka" %% "akka-stream" % v.akka,
//      "com.typesafe.akka" %% "akka-http-experimental" % v.akka,
//      "com.typesafe.akka" %% "akka-http-spray-json-experimental" % v.akka,
//      "com.typesafe.akka" %% "akka-http-testkit" % v.akka,

      "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.9",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.9"
    ),
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    ),
    PB.protoSources in Compile := Seq(file("media-manager-state/src/main/protobuf"))
  )

lazy val `media-manager-app` = project
  .dependsOn(`media-manager-service`)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test
    )
  )

