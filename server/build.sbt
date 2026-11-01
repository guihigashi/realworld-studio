scalaVersion := "3.10.0"

lazy val zioVersion        = "2.1.27"
lazy val zioConfigVersion  = "4.1.0"
lazy val zioLoggingVersion = "2.5.3"

lazy val root = project
  .in(file("."))
  .settings(
    name    := "server",
    version := "0.1.0-SNAPSHOT",
    Test / fork := true,
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"               % zioVersion,
      "dev.zio" %% "zio-test"          % zioVersion % Test,
      "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt"      % zioVersion % Test,

      "dev.zio" %% "zio-config"                % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia"       % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe"       % zioConfigVersion,
      "dev.zio" %% "zio-logging"               % zioLoggingVersion,
      "dev.zio" %% "zio-logging-slf4j2-bridge" % zioLoggingVersion,

      "dev.zio"      %% "zio-http"         % "3.11.6",
      "dev.zio"      %% "zio-interop-cats" % "23.1.0.14",
      "org.tpolecat" %% "skunk-core"       % "2.0.0-RC3",
    ),
  )
