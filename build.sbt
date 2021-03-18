name := "beam-protocols-visualizing"

version := "0.1"

scalaVersion := "2.13.5"

idePackagePrefix := Some("beam.protocolvis")

lazy val root = (project in file("."))
  .settings(
    organization := "dopenkov",
    name := "etl1",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.4",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "2.3.1",
      "com.univocity" % "univocity-parsers" % "2.9.1",
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    testFrameworks += new TestFramework("munit.Framework")
  )