import sbt._
import Keys._
import java.io.PrintWriter
import scala.io.Source

trait BuildCommons {

  lazy val scalaVersionsSettings: Seq[Setting[_]] = Seq(
    crossScalaVersions := Seq("2.13.4", "2.12.13", "2.11.12", "2.10.7"),
    scalaVersion := crossScalaVersions.value.head,
  )

  val runFlickerTests = Option(System.getenv("SCALATEST_RUN_FLICKER_TESTS")).getOrElse("FALSE").toUpperCase == "TRUE"

  val scalaJSVersion = Option(System.getenv("SCALAJS_VERSION")).getOrElse("1.6.0")
  def scalatestJSLibraryDependencies =
    Seq(
      "org.scala-js" %% "scalajs-test-interface" % scalaJSVersion
    )

  val releaseVersion = "3.2.9"

  val previousReleaseVersion = "3.2.8"

  val plusJUnitVersion = "3.2.9.0"
  val plusTestNGVersion = "3.2.9.0"
  val flexmarkVersion = "0.62.2"

  def rootProject: Project

  def scalatestCompatible: Project

  def scalaXmlDependency(theScalaVersion: String): Seq[ModuleID]

  def scalatestLibraryDependencies: Seq[ModuleID]

  def scalaLibraries(theScalaVersion: String): Seq[ModuleID]

  def sharedSettings: Seq[Setting[_]]

  def scalacticDocSettings: Seq[Setting[_]]

  def scalatestDocSettings: Seq[Setting[_]]

  def scalatestJSDocTaskSetting: Setting[_]

  def crossBuildTestLibraryDependencies: sbt.Def.Initialize[Seq[sbt.ModuleID]]

  lazy val projectTitle = settingKey[String]("Name of project to display in doc titles")

  val javaSourceManaged: SettingKey[java.io.File] = sbt.SettingKey[java.io.File]("javaSourceManaged")  

  //
  // Prepares source files for running scaladoc.
  //
  def genDocSources(srcFiles: Seq[File],
                    srcDirs: Seq[File],
                    docsrcDir: File): Seq[File] =
  {
    val scalaFiles =
      for {
        srcFile <- srcFiles
        if srcFile.name.endsWith(".scala")
      } yield {
        val srcPath = srcFile.getPath
        val maybeSourceFile = srcDirs.flatMap(srcFile.relativeTo).headOption
        maybeSourceFile match {
          case Some(docsrcFile) => copyDocFile(srcFile, new File(docsrcDir.asFile, docsrcFile.getPath))
          case None             =>
             throw new RuntimeException("unexpected source path ["+ srcPath +"] not relative to " + srcDirs.mkString("[", ", ", "]"))
        }
      }

    val javaSources = srcFiles.filter(_.name.endsWith(".java")).toSet
    val javaTagFiles = JavaTagDocumenter.docJavaTags(javaSources)

    scalaFiles ++ javaTagFiles
  }

  //
  // Copies a file, doing a little filtering along the way to make
  // destination file suitable for use in generating scaladocs.
  //
  // Returns destination file.
  //
  private def copyDocFile(srcFile: File, destFile: File): File = {
    if (!destFile.exists || (destFile.lastModified < srcFile.lastModified)) {
      IO.createDirectory(file(destFile.getParent))

      val writer = new PrintWriter(destFile)

      try {
        for (line <- Source.fromFile(srcFile).getLines)
          writer.println(line.replaceFirst("@Finders(.*)", ""))
      }
      finally { writer.close }
    }
    destFile
  }

  //
  // Adds customization to scaladocs.
  //
  // Appends additional css to template.css file and copies
  // additional gifs into lib directory.
  //
  // Note: found that adding new gifs into lib directory causes
  // doc task to rebuild scaladocs from scratch each time.
  // Without that it only rebuilds if needed.
  //
  def docTask(docDir: File, resDir: File, projectName: String): File = {
    val docLibDir = docDir / "lib"
    val htmlSrcDir = resDir / "html"
    val cssFile = docLibDir / "template.css"
    val addlCssFile = htmlSrcDir / "addl.css"

    val css = Source.fromFile(cssFile).mkString
    val addlCss = Source.fromFile(addlCssFile).mkString

    if (!css.contains("pre.stHighlighted")) {
      val writer = new PrintWriter(cssFile)

      try {
        writer.println(css)
        writer.println(addlCss)
      }
      finally { writer.close }
    }

    if (projectName.contains("scalatest")) {
      (htmlSrcDir * "*.gif").get.foreach { gif =>
        IO.copyFile(gif, docLibDir / gif.name)
      }
    }
    docDir
  }

  lazy val docsrcDir =
    settingKey[File](
      "Directory to hold processed source files for generating scaladocs")

  val docsrcDirSetting =
     docsrcDir := target.value / "docsrc"

  val docTaskSetting =
    doc in Compile := docTask((doc in Compile).value,
                              (sourceDirectory in Compile).value,
                              name.value)

  def scalatestTestOptions =
    Seq(
      Tests.Argument(TestFrameworks.ScalaTest,
        (
          Seq(
            "-l", "org.scalatest.tags.Slow",
            "-m", "org.scalatest",
            "-m", "org.scalactic",
            "-m", "org.scalactic.anyvals",
            "-m", "org.scalactic.algebra",
            "-m", "org.scalactic.enablers",
            "-m", "org.scalatest.fixture",
            "-m", "org.scalatest.concurrent",
            "-m", "org.scalatest.deprecated",
            "-m", "org.scalatest.events",
            "-m", "org.scalatest.prop",
            "-m", "org.scalatest.tools",
            "-m", "org.scalatest.matchers",
            "-m", "org.scalatest.matchers.should",
            "-m", "org.scalatest.matchers.must",
            "-m", "org.scalatest.matchers.dsl",
            "-m", "org.scalatest.verbs",
            "-m", "org.scalatest.suiteprop",
            "-m", "org.scalatest.path",
            "-m", "org.scalatest.exceptions",
            "-m", "org.scalatest.time",
            "-m", "org.scalatest.words",
            "-m", "org.scalatest.enablers",
            "-m", "org.scalatest.expectations",
            "-m", "org.scalatest.diagrams",
            "-m", "org.scalatest.featurespec",
            "-m", "org.scalatest.flatspec",
            "-m", "org.scalatest.freespec",
            "-m", "org.scalatest.funspec",
            "-m", "org.scalatest.funsuite",
            "-m", "org.scalatest.propspec",
            "-m", "org.scalatest.wordspec",
            "-oDIF",
            "-W", "120", "60",
            "-h", "target/html",
            "-u", "target/junit",
            "-fW", "target/result.txt"
          ) ++ 
          (if (runFlickerTests) Seq.empty[String] else Seq("-l", "org.scalatest.tags.Flicker")) 
        ): _*
      )
    )

  def scalatestTestJSNativeOptions =
    Seq(
      Tests.Argument(TestFrameworks.ScalaTest,
        (
          Seq(
            "-l", "org.scalatest.tags.Slow",
            "-m", "org.scalatest",
            "-m", "org.scalactic",
            "-m", "org.scalactic.anyvals",
            "-m", "org.scalactic.algebra",
            "-m", "org.scalactic.enablers",
            "-m", "org.scalatest.fixture",
            "-m", "org.scalatest.concurrent",
            "-m", "org.scalatest.events",
            "-m", "org.scalatest.prop",
            "-m", "org.scalatest.tools",
            "-m", "org.scalatest.matchers",
            "-m", "org.scalatest.matchers",
            "-m", "org.scalatest.matchers.should",
            "-m", "org.scalatest.matchers.must",
            "-m", "org.scalatest.matchers.dsl",
            "-m", "org.scalatest.verbs",
            "-m", "org.scalatest.suiteprop",
            "-m", "org.scalatest.path",
            "-m", "org.scalatest.exceptions",
            "-m", "org.scalatest.time",
            "-m", "org.scalatest.words",
            "-m", "org.scalatest.enablers",
            "-m", "org.scalatest.expectations",
            "-m", "org.scalatest.diagrams",
            "-m", "org.scalatest.featurespec",
            "-m", "org.scalatest.flatspec",
            "-m", "org.scalatest.freespec",
            "-m", "org.scalatest.funspec",
            "-m", "org.scalatest.funsuite",
            "-m", "org.scalatest.propspec",
            "-m", "org.scalatest.wordspec",
            "-oDIF"
          ) ++ 
          (if (runFlickerTests) Seq.empty[String] else Seq("-l", "org.scalatest.tags.Flicker")) 
        ): _*
      )
    )                                
}
