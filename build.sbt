// Projects
// --------
lazy val coroutines = project
  .in(file("."))
  .settings(moduleName := "coroutines")
  .settings(sharedSettings: _*)
  .settings(testingSettings: _*)
  .configs(Benchmarks)
  .settings(inConfig(Benchmarks)(Defaults.testSettings): _*)
  .aggregate(coroutinesCommon)
  .dependsOn(coroutinesCommon % "compile->compile;test->test")

lazy val coroutinesCommon = project
  .in(file("coroutines-common"))
  .settings(moduleName := "coroutines-common")
  .settings(sharedSettings: _*)

lazy val coroutinesExtra = project
  .in(file("coroutines-extra"))
  .settings(sharedSettings: _*)
  .dependsOn(coroutines % "compile->compile;test->test")

// Settings (Shared)
// -----------------
lazy val sharedSettings = Seq(
  scalaVersion := "2.11.7",
  version := "0.8-SNAPSHOT",
  organization := "com.storm-enroute",
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-optimise",
    "-Yinline-warnings"
  ),
  resolvers ++= Seq(
    "Sonatype OSS Snapshots" at
      "https://oss.sonatype.org/content/repositories/snapshots",
    "Sonatype OSS Releases" at
      "https://oss.sonatype.org/content/repositories/releases"
  ),
  libraryDependencies ++= Seq(
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2",
    "org.scala-lang" % "scala-reflect" % "2.11.4"
  )
)

// Settings (Tests)
// ----------------
lazy val Benchmarks = config("bench")
  .extend(Test)

lazy val scalaMeterFramework = new TestFramework("org.scalameter.ScalaMeterFramework")

lazy val testingSettings = Seq(
  testFrameworks in ThisBuild += scalaMeterFramework,
  testOptions in ThisBuild += Tests.Argument(scalaMeterFramework, "-silent"),

  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    "com.storm-enroute" %% "scalameter" % "0.9-SNAPSHOT" % "test;bench",
    "org.scala-lang.modules" %% "scala-async" % "0.9.5" % "test;bench"
  )
)
