enablePlugins(JavaServerAppPackaging)

name := "goticks"

version := "1.0"

organization := "com.goticks"

scalaVersion := "2.13.3"

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-Xfatal-warnings",
  "-feature",
  "-language:_"
)

libraryDependencies ++= {
  val akkaVersion     = "2.6.9"
  val akkaHttpVersion = "10.2.0"
  Seq(
    "com.typesafe.akka" %% "akka-actor-typed"     % akkaVersion,
    "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
    "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-slf4j"           % akkaVersion,
    "ch.qos.logback"    % "logback-classic"       % "1.1.3",
    "com.typesafe.akka" %% "akka-testkit"         % akkaVersion % "test",
    "org.scalatest"     %% "scalatest"            % "3.2.0" % "test"
  )
}

// Assembly settings
mainClass in assembly := Some("com.goticks.Main")

assemblyJarName in assembly := "goticks.jar"
