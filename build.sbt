import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.5.1"

lazy val microservice = Project("digital-platform-reporting", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin, ScalaxbPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s,src=src_managed/.*:s",
    scalaxbGenerateDispatchClient := false
  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings *)
  .settings(PlayKeys.playDefaultPort := 20004)
  .settings(RoutesKeys.routesImport ++= Seq(
    "models._",
    "java.time.Year"
  ))

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)
