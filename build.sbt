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
  .enablePlugins(ProtobufPlugin)

lazy val `media-manager-service` = project
  .dependsOn(`media-manager-state`)
  .settings(
    // TODO: Clean this up and remove the unnecessary dependencies
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test,

      "com.typesafe.akka" %% "akka-http"   % "10.1.8",
      "com.typesafe.akka" %% "akka-stream" % "2.5.19",
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.8",
      "org.typelevel" %% "cats-core" % "1.6.1",


      "com.github.kokorin.jaffree" % "jaffree" % "0.8.3",

      "org.slf4j" % "slf4j-api" % "1.7.25",

      "org.apache.tika" % "tika-core" % "1.21"
    )
  )

lazy val `media-manager-state` = project
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test,
      "junit" % "junit" % v.junit % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "com.typesafe.akka" %% "akka-typed-testkit" % v.akkaTyped  % Test,

      "com.typesafe.akka" %% "akka-typed" % v.akkaTyped,
      "com.typesafe.akka" %% "akka-cluster" % v.akka,
      "com.typesafe.akka" %% "akka-distributed-data" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-tools" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-sharding" % v.akka,
      "com.typesafe.akka" %% "akka-persistence" % v.akka,
      "com.typesafe.akka" %% "akka-persistence-query" % v.akka,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % v.cassandraPlugin,
      // this allows us to start cassandra from the sample
      "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % v.cassandraPlugin,
    )
  )

lazy val `media-manager-app` = project
  .dependsOn(`media-manager-service`)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test
    )
  )
