package xerial.sbt

import java.io._
import java.time.format.{DateTimeFormatterBuilder, SignStyle}
import java.time.temporal.ChronoField._
import java.util.Locale

import sbt._

package object pack {

  implicit class ArchiveFile(f: File) {
    def toList: List[String] = Option(f.getParentFile) match {
      case None => f.getName :: Nil
      case Some(p) => p.toList :+ f.getName
    }

    def toString(separator: String): String =
      toList.mkString(separator)
  }

  def rpath(base: File, f: RichFile, separator: String = File.separator): String =
    f.relativeTo(base).getOrElse(f.asFile).toString(separator)

  private[pack] def getFromAllProjects[T](targetTask: TaskKey[T], state: State): Task[Seq[(T, ProjectRef)]] =
    getFromSelectedProjects(targetTask, state, Seq.empty)

  private[pack] def getFromSelectedProjects[T](targetTask: TaskKey[T], state: State, exclude: Seq[String]): Task[Seq[(T, ProjectRef)]] = {
    val extracted = Project.extract(state)
    val structure = extracted.structure

    def allProjectRefs(currentProject: ProjectRef): Seq[ProjectRef] = {
      def isExcluded(p: ProjectRef) = exclude.contains(p.project)

      val children = Project.getProject(currentProject, structure).toSeq.flatMap(_.uses)
      (currentProject +: (children flatMap allProjectRefs)) filterNot isExcluded
    }

    val projects: Seq[ProjectRef] = allProjectRefs(extracted.currentRef).distinct
    projects.map(p => Def.task {
      ((targetTask in p).value, p)
    } evaluate structure.data).join.asInstanceOf[Task[Seq[(T, ProjectRef)]]]
  }

  private[pack] val humanReadableTimestampFormatter = new DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
    .appendLiteral('-')
    .appendValue(MONTH_OF_YEAR, 2)
    .appendLiteral('-')
    .appendValue(DAY_OF_MONTH, 2)
    .appendLiteral(' ')
    .appendValue(HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(MINUTE_OF_HOUR, 2)
    .appendLiteral(':')
    .appendValue(SECOND_OF_MINUTE, 2)
    .appendOffset("+HHMM", "Z")
    .toFormatter(Locale.US)

  private[pack] def pascalCaseSplit(s: List[Char]): List[String] =
    if (s.isEmpty) {
      Nil
    }
    else if (!s.head.isUpper) {
      val (w, tail) = s.span(!_.isUpper)
      w.mkString :: pascalCaseSplit(tail)
    }
    else if (s.tail.headOption.forall(!_.isUpper)) {
      val (w, tail) = s.tail.span(!_.isUpper)
      (s.head :: w).mkString :: pascalCaseSplit(tail)
    }
    else {
      val (w, tail) = s.span(_.isUpper)
      w.init.mkString :: pascalCaseSplit(w.last :: tail)
    }

  private[pack] def hyphenize(s: String): String =
    pascalCaseSplit(s.toList).map(_.toLowerCase).mkString("-")

  private[pack] def resolveJarName(m: ModuleEntry, convention: String) = {
    convention match {
      case "original" => m.originalFileName
      case "full" => m.fullJarName
      case "no-version" => m.noVersionJarName
      case _ => m.jarName
    }
  }
}
