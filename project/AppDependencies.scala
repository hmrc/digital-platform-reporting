import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.5.0"
  private val hmrcMongoVersion = "2.2.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"          % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-work-item-repo-play-30"  % hmrcMongoVersion,
    "com.beachape"            %% "enumeratum-play"                    % "1.8.2",
    "org.typelevel"           %% "cats-core"                          % "2.12.0",
    "javax.xml.bind"          %  "jaxb-api"                           % "2.3.1",
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongoVersion,
    "org.scalacheck"          %% "scalacheck"                 % "1.18.0"
  ).map(_ % Test)

  val it: Seq[Nothing] = Seq.empty
}
