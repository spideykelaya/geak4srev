import org.scalajs.linker.interface.ModuleSplitStyle

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.6.2"
Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val backend = (project in file("backend"))
  .settings(
    name := "backend",
    scalaVersion := "3.6.2",
    libraryDependencies ++= Seq(
      "org.apache.poi" % "poi-ooxml" % "5.2.5",
      "com.lihaoyi" %% "cask" % "0.9.1"
    )
  )

lazy val root = (project in file("."))
  .aggregate(backend)
  .settings(
    name                            := "geak4s",
    sourcesInBase                   := false,
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.SmallModulesFor(List("geak4s"))
        )
    },
    libraryDependencies ++= Seq(
      "com.raquo"   %%% "laminar"            % "17.2.0",
      "be.doeraene" %%% "web-components-ui5" % "2.1.0",
      "org.apache.poi" % "poi-ooxml" % "5.2.3",
      "io.circe"    %%% "circe-core"         % "0.14.10",
      "io.circe"    %%% "circe-generic"      % "0.14.10",
      "io.circe"    %%% "circe-parser"       % "0.14.10"
    )
  )
  .enablePlugins(ScalaJSPlugin)



