//--------------------------------------
//
// Pack.scala
// Since: 2012/11/19 4:12 PM
//
//--------------------------------------

package xerial.sbt.pack

import java.nio.file.Files
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.Date

import scala.util.Try
import scala.util.matching.Regex

import sbt.Keys._
import sbt.complete.DefaultParsers._
import sbt.{Def, _}
import xerial.sbt.pack.LaunchScript.MakefileOpts

/**
  * Plugin for packaging projects
  *
  * @author Taro L. Saito
  */
object PackPlugin extends AutoPlugin with PackArchive {

  override def trigger = allRequirements

  object autoImport {
    val pack = taskKey[File]("create a distributable package of the project")
    val packInstall = inputKey[Int]("pack and install")
    val packTargetDir = settingKey[File]("target directory to pack default is target")
    val packDir = settingKey[String]("pack directory name")

    val packMain = TaskKey[Map[String, String]]("prog_name -> main class table")
    val packMainDiscovered = TaskKey[Map[String, String]]("discovered prog_name -> main class table")
    val packExclude = SettingKey[Seq[String]]("pack-exclude", "specify projects whose dependencies will be excluded when packaging")
    val packExcludeLibJars = SettingKey[Seq[String]]("pack-exclude", "specify projects to exclude when packaging.  Its dependencies will be processed")
    val packExcludeJars = SettingKey[Seq[String]]("pack-exclude-jars", "specify jar file name patterns to exclude when packaging")
    val packExcludeArtifactTypes = settingKey[Seq[String]]("specify artifact types (e.g. javadoc) to exclude when packaging")
    val packLibJars = TaskKey[Seq[(File, ProjectRef)]]("pack-lib-jars")
    val packGenerateBashFile = settingKey[Boolean]("Generate BASH file launch scripts")
    val packGenerateWindowsBatFile = settingKey[Boolean]("Generate BAT file launch scripts for Windows")
    val packGenerateMakefile = settingKey[Boolean]("Generate Makefile")

    val packBatDefaultTemplate = settingKey[ScriptSource]("BAT file template")
    val packBashDefaultTemplate = settingKey[ScriptSource]("BASH file template")
    val packMakeDefaultTemplate = settingKey[ScriptSource]("MAKEFILE file template")

    val packBatCustomTemplates = settingKey[Map[String, ScriptSource]]("BAT file templates for each main class")
    val packBashCustomTemplates = settingKey[Map[String, ScriptSource]]("BASH file template for each main class")
    //    val packMakeCustomTemplates = settingKey[Map[String, ScriptSource]]("MAKEFILE file template for each main class")

    val packMacIconFile = SettingKey[String]("pack-mac-icon-file", "icon file name for Mac")
    val packResourceDir = SettingKey[Map[File, String]](s"pack-resource-dir", "pack resource directory. default = Map({projectRoot}/src/pack -> \"\")")
    val packAllUnmanagedJars = taskKey[Seq[(Classpath, ProjectRef)]]("all unmanaged jar files")
    val packModuleEntries = taskKey[Seq[ModuleEntry]]("modules that will be packed")
    val packJvmOpts = SettingKey[Map[String, Seq[String]]]("pack-jvm-opts")
    val packUseJavaExec = SettingKey[Map[String, Boolean]]("pack-use-java-exec")
    val packExtraClasspath = SettingKey[Map[String, Seq[String]]]("pack-extra-classpath")
    val packExpandedClasspath = settingKey[Boolean]("Expands the wildcard classpath in launch scripts to point at specific libraries")
    val packJarNameConvention = SettingKey[String]("pack-jarname-convention",
      "default: (artifact name)-(version).jar; original: original JAR name; full: (organization).(artifact name)-(version).jar; no-version: (organization).(artifact name).jar")
    val packDuplicateJarStrategy = SettingKey[String]("deal with duplicate jars. default to use latest version",
      "latest: use the jar with a higher version; exit: exit the task with error")
    val packCopyDependenciesTarget = settingKey[File]("target folder used by the <packCopyDependencies> task.")
    val packCopyDependencies = taskKey[Unit](
      """just copies the dependencies to the <packCopyDependencies> folder.
        		|Compared to the <pack> task, it doesn't try to create scripts.
      	  """.stripMargin)
    val packCopyDependenciesUseSymbolicLinks = taskKey[Boolean](
      """use symbolic links instead of copying for <packCopyDependencies>.
        		|The use of symbolic links allows faster processing and save disk space.
      	  """.stripMargin)

    val packArchivePrefix = SettingKey[String]("prefix of (prefix)-(version).(format) archive file name")
    val packArchiveName = SettingKey[String]("archive file name. Default is (project-name)-(version)")
    val packArchiveStem = SettingKey[String]("directory name within the archive. Default is (archive-name)")
    val packArchiveExcludes = SettingKey[Seq[String]]("List of excluding files from the archive")
    val packArchiveTgzArtifact = SettingKey[Artifact]("tar.gz archive artifact")
    val packArchiveTbzArtifact = SettingKey[Artifact]("tar.bz2 archive artifact")
    val packArchiveTxzArtifact = SettingKey[Artifact]("tar.xz archive artifact")
    val packArchiveZipArtifact = SettingKey[Artifact]("zip archive artifact")
    val packArchiveTgz = TaskKey[File]("pack-archive-tgz", "create a tar.gz archive of the distributable package")
    val packArchiveTbz = TaskKey[File]("pack-archive-tbz", "create a tar.bz2 archive of the distributable package")
    val packArchiveTxz = TaskKey[File]("pack-archive-txz", "create a tar.xz archive of the distributable package")
    val packArchiveZip = TaskKey[File]("pack-archive-zip", "create a zip archive of the distributable package")
    val packArchive = TaskKey[Seq[File]]("pack-archive", "create a tar.gz and a zip archive of the distributable package")
  }

  private val targetFolderParser: complete.Parser[Option[String]] =
    (Space ~> token(StringBasic, "(target folder)")).?.!!!("invalid input. please input target folder name")

  override lazy val projectSettings: Seq[Def.Setting[_]] = packSettings ++ packArchiveSettings

  import autoImport._

  lazy val packSettings: Seq[Def.Setting[_]] = Seq[Def.Setting[_]](
    packTargetDir := target.value,
    packDir := "pack",

    packMain := packMainDiscovered.value,
    packExclude := Seq.empty,
    packExcludeLibJars := Seq.empty,
    packExcludeJars := Seq.empty,
    packExcludeArtifactTypes := Seq("source", "javadoc", "test"),
    packMacIconFile := "icon-mac.png",
    packResourceDir := Map(baseDirectory.value / "src/pack" -> ""),
    packJvmOpts := Map.empty,
    packUseJavaExec := Map.empty,
    packExtraClasspath := Map.empty,
    packExpandedClasspath := false,
    packJarNameConvention := "default",
    packDuplicateJarStrategy := "latest",
    packGenerateBashFile := true,
    packGenerateWindowsBatFile := true,
    packGenerateMakefile := true,

    packBatDefaultTemplate := ScriptSource(getClass.getResource("/xerial/sbt/pack/templates/batTemplate.ssp")),
    packBashDefaultTemplate := ScriptSource(getClass.getResource("/xerial/sbt/pack/templates/bashTemplate.ssp")),
    packMakeDefaultTemplate := ScriptSource(getClass.getResource("/xerial/sbt/pack/templates/makefileTemplate.ssp")),

    packBatCustomTemplates := Map.empty[String, ScriptSource],
    packBashCustomTemplates := Map.empty[String, ScriptSource],

    packMainDiscovered := Def.taskDyn {
      val mainClasses = getFromSelectedProjects(discoveredMainClasses in Compile, state.value, packExclude.value)
      Def.task {
        mainClasses.value.flatMap(_._1.map(mainClass => hyphenize(mainClass.split('.').last) -> mainClass).toMap).toMap
      }
    }.value,
    packAllUnmanagedJars := Def.taskDyn {
      val allUnmanagedJars = getFromSelectedProjects(unmanagedJars in Runtime, state.value, packExclude.value)
      Def.task {
        allUnmanagedJars.value
      }
    }.value,
    packLibJars := Def.taskDyn {
      val libJars = getFromSelectedProjects(packageBin in Runtime, state.value, packExcludeLibJars.value)
      Def.task {
        libJars.value
      }
    }.value,
    (mappings in pack) := Seq.empty,
    packModuleEntries := {
      val out = streams.value
      val jarExcludeFilter: Seq[Regex] = packExcludeJars.value.map(_.r)

      def isExcludeJar(name: String): Boolean = {
        val toExclude = jarExcludeFilter.exists(pattern => pattern.findFirstIn(name).isDefined)
        if (toExclude) {
          out.log.info(s"Exclude $name from the package")
        }
        toExclude
      }

      val df = configurationFilter(name = "runtime") //&&

      val dependentJars =
        for {
          c <- update.value.filter(df).configurations
          m <- c.modules if !m.evicted
          (artifact, file) <- m.artifacts
          if !packExcludeArtifactTypes.value.contains(artifact.`type`) && !isExcludeJar(file.name)
        } yield {
          val mid = m.module
          ModuleEntry(mid.organization, mid.name, VersionString(mid.revision), artifact.name, artifact.classifier, file)
        }

      implicit val versionStringOrdering: DefaultVersionStringOrdering.type = DefaultVersionStringOrdering
      val distinctDpJars = dependentJars
        .groupBy(_.noVersionModuleName)
        .flatMap {
          case (key, entries) if entries.groupBy(_.revision).size == 1 => entries
          case (key, entries) =>
            val revisions = entries.groupBy(_.revision).keys.toList.sorted
            val latestRevision = revisions.last
            packDuplicateJarStrategy.value match {
              case "latest" =>
                out.log.debug(s"Version conflict on $key. Using $latestRevision (found ${revisions.mkString(", ")})")
                entries.filter(_.revision == latestRevision)
              case "exit" =>
                sys.error(s"Version conflict on $key (found ${revisions.mkString(", ")})")
              case x =>
                sys.error("Unknown duplicate JAR strategy '%s'".format(x))
            }
        }
      distinctDpJars.toSeq
    },
    packCopyDependenciesUseSymbolicLinks := true,
    packCopyDependenciesTarget := target.value / "lib",
    packCopyDependencies := {
      val log = streams.value.log

      val distinctDpJars = packModuleEntries.value.map(_.file)
      val unmanaged = packAllUnmanagedJars.value.flatMap {
        _._1
      }.map {
        _.data
      }
      val copyDepTargetDir = packCopyDependenciesTarget.value
      val useSymlink = packCopyDependenciesUseSymbolicLinks.value

      copyDepTargetDir.mkdirs()
      IO.delete((copyDepTargetDir * "*.jar").get)
      (distinctDpJars ++ unmanaged) foreach { d ⇒
        log debug s"Copying ${d.getName}"
        val dest = copyDepTargetDir / d.getName
        if (useSymlink) {
          Files.createSymbolicLink(dest.toPath, d.toPath)
        }
        else {
          IO.copyFile(d, dest)
        }
      }
      val libs = packLibJars.value.map(_._1)
      libs.foreach(l ⇒ IO.copyFile(l, copyDepTargetDir / l.getName))

      log info s"Copied ${distinctDpJars.size + libs.size} jars to $copyDepTargetDir"
    },
    pack := {
      val out = streams.value

      val distDir: File = packTargetDir.value / packDir.value
      out.log.info("Creating a distributable package in " + rpath(baseDirectory.value, distDir))
      IO.delete(distDir)
      distDir.mkdirs()

      // Create target/pack/lib folder
      val libDir = distDir / "lib"
      libDir.mkdirs()

      // Copy project jars
      val base: File = baseDirectory.value
      out.log.info("Copying libraries to " + rpath(base, libDir))
      val libs: Seq[File] = packLibJars.value.map(_._1)
      out.log.info("project jars:\n" + libs.map(path => rpath(base, path)).mkString("\n"))
      libs.foreach(l => IO.copyFile(l, libDir / l.getName))

      // Copy dependent jars

      val distinctDpJars = packModuleEntries.value
      out.log.info("project dependencies:\n" + distinctDpJars.mkString("\n"))
      val jarNameConvention = packJarNameConvention.value
      for (m <- distinctDpJars) {
        val targetFileName = resolveJarName(m, jarNameConvention)
        IO.copyFile(m.file, libDir / targetFileName, preserveLastModified = true)
      }

      // Copy unmanaged jars in ${baseDir}/lib folder
      out.log.info("unmanaged dependencies:")
      for ((m, projectRef) <- packAllUnmanagedJars.value; um <- m; f = um.data) {
        out.log.info(f.getPath)
        IO.copyFile(f, libDir / f.getName, preserveLastModified = true)
      }

      // Copy explicitly added dependencies
      val mapped: Seq[(File, String)] = (mappings in pack).value
      out.log.info("explicit dependencies:")
      for ((file, path) <- mapped) {
        out.log.info(file.getPath)
        IO.copyFile(file, distDir / path, preserveLastModified = true)
      }

      // Create target/pack/bin folder
      val binDir = distDir / "bin"
      out.log.info("Create a bin folder: " + rpath(base, binDir))
      binDir.mkdirs()

      def write(path: String, content: String) {
        val p = distDir / path
        out.log.info("Generating %s".format(rpath(base, p)))
        IO.write(p, content)
      }

      // Create launch scripts
      out.log.info("Generating launch scripts")
      val mainTable: Map[String, String] = packMain.value
      if (mainTable.isEmpty) {
        out.log.warn("No mapping (program name) -> MainClass is defined. Please set packMain variable (Map[String, String]) in your sbt project settings.")
      }

      val progVersion = version.value
      val macIconFile = packMacIconFile.value

      // Check the current Git revision
      val gitRevision: String = Try {
        out.log.info("Checking the git revison of the current project")
        sys.process.Process("git rev-parse HEAD").!!
      }.getOrElse("unknown").trim

      val pathSeparator = "${PSEP}"
      val expandedCp = packExpandedClasspath.value

      // Render script via Scalate template
      for ((scriptName, mainClass) <- mainTable) {
        out.log.info("main class for %s: %s".format(scriptName, mainClass))

        def expandedClasspath: Seq[String] = {
          val projJars = libs.map(l => "lib/" + l.getName)
          val depJars = distinctDpJars.map(m => "lib/" + resolveJarName(m, jarNameConvention))
          val unmanagedJars = for ((m, projectRef) <- packAllUnmanagedJars.value; um <- m; f = um.data) yield {
            "lib/" + f.getName
          }
          projJars ++ depJars ++ unmanagedJars
        }

        val expandedClasspathM = if (expandedCp) {
          expandedClasspath
        }
        else {
          Nil
        }

        val scriptOpts = LaunchScript.Opts(
          progName = scriptName,
          progVersion = progVersion,
          progRevision = gitRevision,
          mainClass = mainClass,
          extraClasspath = packExtraClasspath.value.getOrElse(scriptName, Nil),
          expandedClasspath = expandedClasspathM,
          useJavaExec = packUseJavaExec.value.getOrElse(scriptName, false),
          jvmOpts = packJvmOpts.value.getOrElse(scriptName, Nil).map("\"%s\"".format(_)),
          macIconFile = macIconFile
        )

        val progName = scriptName.replaceAll(" ", "") // remove white spaces
        val bashTemplSource = packBashCustomTemplates.value.getOrElse(scriptName, packBashDefaultTemplate.value)
        if (packGenerateBashFile.value) {
          val launchScript = LaunchScript.generateScript(bashTemplSource, scriptOpts)
          write(s"bin/$progName", launchScript)
        }

        // Create BAT file
        val batTemplSource = packBatCustomTemplates.value.getOrElse(scriptName, packBatDefaultTemplate.value)
        if (packGenerateWindowsBatFile.value) {
          val batScript = LaunchScript.generateScript(batTemplSource, scriptOpts)
          write(s"bin/$progName.bat", batScript)
        }
      }

      // Copy resources
      val otherResourceDirs = packResourceDir.value
      val binScriptsDir = otherResourceDirs.map(_._1 / "bin").filter(_.exists)
      out.log.info(s"packed resource directories = ${otherResourceDirs.keys.mkString(",")}")

      val projectName = name.value
      val makefileTemplSource = packMakeDefaultTemplate.value
      if (packGenerateMakefile.value) {
        // Create Makefile
        val additionalScripts = binScriptsDir.flatMap(_.listFiles).map(_.getName)
        val makefileOpts = MakefileOpts(projectName, (mainTable.keys ++ additionalScripts).toSeq)
        val makefile = LaunchScript.generateMakefile(makefileTemplSource, makefileOpts)

        write("Makefile", makefile)
      }

      // Retrieve build time
      val systemZone = ZoneId.systemDefault().normalized()
      val timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(new Date().getTime), systemZone)
      val buildTime = humanReadableTimestampFormatter.format(timestamp)

      // Output the version number and Git revision
      write("VERSION", s"version:=$progVersion\nrevision:=$gitRevision\nbuildTime:=$buildTime\n")

      // Copy other scripts
      otherResourceDirs.foreach { otherResourceDir =>
        val from = otherResourceDir._1
        val to = otherResourceDir._2 match {
          case "" => distDir
          case p => distDir / p
        }
        IO.copyDirectory(from, to, overwrite = true, preserveLastModified = true)
      }

      // chmod +x the scripts in bin directory
      binDir.listFiles.foreach(_.setExecutable(true, false))

      out.log.info("done.")
      distDir
    },
    packInstall := {
      val arg: Option[String] = targetFolderParser.parsed
      val packDir = pack.value
      val cmd = arg match {
        case Some(trgt) =>
          s"make install PREFIX=$trgt"
        case None =>
          s"make install"
      }
      sys.process.Process(cmd, Some(packDir)).!
    }
  )
}
