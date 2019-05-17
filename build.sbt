ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := "2.12.7"
ThisBuild / organization := "com.callhandling"
ThisBuild / scalacOptions:= Seq("-Xexperimental", "-Xlint:_", "-unchecked", "-deprecation", "-feature", "-target:jvm-1.8")

lazy val dependencies =
  new {
    val scalatest      = "org.scalatest"              %% "scalatest"               % "3.0.7"
  }

lazy val mediaFileEncoder = (project in file("."))
  .aggregate(mediaManagerService, mediaManageState, mediaManagerApp)
  .enablePlugins(JavaAppPackaging)

lazy val mediaManagerService = (project in file("mediaManagerService"))
  .dependsOn(mediaManageState)
  .settings(
    name := "mediaManagerService",
    libraryDependencies += dependencies.scalatest % Test,
  )

lazy val mediaManageState = (project in file("mediaManageState"))
  .settings(
    name := "mediaManageState",
    libraryDependencies += dependencies.scalatest % Test,
  )

lazy val mediaManagerApp = (project in file("mediaManagerApp"))
  .dependsOn(mediaManagerService)
  .settings(
    name := "mediaManagerApp",
    libraryDependencies += dependencies.scalatest % Test,
  )

