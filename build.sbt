ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / organization := "com.callhandling"

lazy val v = new {
  val akka = "2.5.22"
  val akkaHttp = "10.1.8"
  val scalatest = "3.0.7"
  val junit = "4.12"
  val cassandraPlugin = "0.96"
  val gatling = "3.1.2"
  val jackson = "2.9.9"
}

// This is an application with a main method
scalaJSUseMainModuleInitializer := true
javaOptions in Gatling := overrideDefaultJavaOptions("-Xms1024m", "-Xmx2048m")
lazy val `media-file-encoder` = project.in(file("."))
  .aggregate(`media-manager-service`, `media-manager-state`, `media-manager-app`)
  .enablePlugins(JavaAppPackaging)
  // issue: double entry on compiled protobuf source folders, remove the other one
  .enablePlugins(ProtobufPlugin) // protobuf:protobufGenerate
  .enablePlugins(GatlingPlugin) // gatling:test
  .enablePlugins(ScalaJSPlugin)

lazy val `media-manager-service` = project
  .dependsOn(`media-manager-state`)
  .enablePlugins(GatlingPlugin)
  .settings(
    // TODO: Clean this up and remove the unnecessary dependencies
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test,
      "io.gatling.highcharts" % "gatling-charts-highcharts" % v.gatling % Test,
      "io.gatling" % "gatling-test-framework" % v.gatling % Test,

      "com.typesafe.akka" %% "akka-actor" % v.akka,
      "com.typesafe.akka" %% "akka-stream" % v.akka,
      "com.typesafe.akka" %% "akka-http"   % v.akkaHttp,
      "com.typesafe.akka" %% "akka-http-spray-json" % v.akkaHttp,

      "com.github.kokorin.jaffree" % "jaffree" % "0.8.3",

      "org.slf4j" % "slf4j-api" % "1.7.25",

      "org.apache.tika" % "tika-core" % "1.21",

      "com.typesafe.akka" %% "akka-actor-typed" % v.akka,
      "com.typesafe.akka" %% "akka-stream-typed" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-typed" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % v.akka,
      "com.typesafe.akka" %% "akka-persistence-typed" % v.akka,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % v.cassandraPlugin,
      // this allows us to start cassandra from the sample
      "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % v.cassandraPlugin,
      "de.heikoseeberger" %% "akka-http-jackson" % "1.25.2",
      "com.fasterxml.jackson.core" % "jackson-databind" % v.jackson,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % v.jackson,

      "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "1.0.2",
    )
  )

lazy val `media-manager-state` = project
  .settings(
    resolvers += Resolver.bintrayRepo("julien-lafont", "maven"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test,
      "junit" % "junit" % v.junit % Test,
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % v.akka % Test,

      "com.typesafe.akka" %% "akka-actor" % v.akka,
      "com.typesafe.akka" %% "akka-actor-typed" % v.akka,
      "com.typesafe.akka" %% "akka-stream" % v.akka,
      "com.typesafe.akka" %% "akka-stream-typed" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-typed" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % v.akka,
      "com.typesafe.akka" %% "akka-persistence-typed" % v.akka,
      "com.typesafe.akka" %% "akka-persistence-query" % v.akka,
      "com.typesafe.akka" %% "akka-persistence-cassandra" % v.cassandraPlugin,
      // this allows us to start cassandra from the sample
      "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % v.cassandraPlugin,

      "com.typesafe.akka" %% "akka-http" % v.akkaHttp,

      "de.heikoseeberger" %% "akka-http-jackson" % "1.25.2",
      "com.fasterxml.jackson.core" % "jackson-databind" % v.jackson,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % v.jackson,

      "ws.schild" % "jave-core" % "2.4.6",
      "ws.schild" % "jave-native-linux64" % "2.4.6",
    )
  )
  .enablePlugins(ProtobufPlugin)

lazy val `media-manager-app` = project
  .dependsOn(`media-manager-service`)
  .settings(
    libraryDependencies ++= Seq(
//      "org.scala-js" %%% "scalajs-dom" % "0.9.7"
    )
  )

