import sbt._

object Dependencies {
  // Dependencies
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.7"
  lazy val akkaVersion = "2.5.22"
  lazy val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  lazy val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.1.8"
  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % "2.5.19"
  lazy val all = Seq(scalaTest % Test, akkaActor, akkaTestkit, akkaHttp, akkaStream)
}
