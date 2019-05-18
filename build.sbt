import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / organization := "com.callhandling"

lazy val v = new {
  val akka = "2.5.22"
  val scalatest = "3.0.7"
}

lazy val `media-file-encoder` = project.in(file("."))
  .aggregate(`media-manager-service`, `media-manager-state`, `media-manager-app`)
  .enablePlugins(JavaAppPackaging)

lazy val `media-manager-service` = project
  .dependsOn(`media-manager-state`)
  .settings(multiJvmSettings: _*)
  .settings(
    scalacOptions in Compile ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m", "-Djava.library.path=./target/native"),
    fork in run := true,
    mainClass in (Compile, run) := Some("sample.sharding.ShardingApp"),
    // disable parallel tests
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test
    )
  )
  .configs (MultiJvm)

lazy val `media-manager-state` = project
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test,
      "com.typesafe.akka" %% "akka-actor" % v.akka,
      "com.typesafe.akka" %% "akka-remote" % v.akka,
      "com.typesafe.akka" %% "akka-cluster" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-metrics" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-tools" % v.akka,
      "com.typesafe.akka" %% "akka-cluster-sharding" % v.akka,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % v.akka,
      "io.kamon" % "sigar-loader" % "1.6.6-rev002"
    )
  )

lazy val `media-manager-app` = project
  .dependsOn(`media-manager-service`)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % v.scalatest % Test
    )
  )