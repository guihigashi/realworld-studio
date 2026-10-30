scalaVersion := "3.10.0"

lazy val zioVersion = "2.1.27"

lazy val root = project
  .in(file("."))
  .settings(
    name    := "server",
    version := "0.1.0-SNAPSHOT",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"          % zioVersion,
      "dev.zio" %% "zio-test"     % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      "dev.zio" %% "zio-http"     % "3.11.6",
    ),
  )
