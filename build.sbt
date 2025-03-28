import play.sbt.routes.RoutesKeys
import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.5.1"

lazy val microservice = Project("digital-platform-reporting", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin, ScalaxbPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalacOptions += "-Wconf:src=routes/.*:s,src=src_managed/.*:s,msg=Flag.*repeatedly:s",
    scalaxbGenerateDispatchClient := false
  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings *)
  .settings(PlayKeys.playDefaultPort := 20004)
  .settings(RoutesKeys.routesImport ++= Seq(
    "models.*",
    "models.processingStatusQueryStringBindable",
    "models.setQueryStringBindable",
    "java.time.Year",
    "uk.gov.hmrc.mongo.workitem.ProcessingStatus"
  ))

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)
