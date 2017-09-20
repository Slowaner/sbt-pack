package xerial.sbt.pack

import org.fusesource.scalate.{Template, TemplateEngine, TemplateSource}
import sbt.URI

object LaunchScript {

  /*def generateLaunchScript(opts: Opts, expandedClasspath: Option[String]): String = {
    val content = xerial.sbt.pack.txt.launch.render(opts, expandedClasspath)
    content.toString
  }

  def generateBatScript(opts: Opts, expandedClasspath: Option[String]): String = {
    val content = xerial.sbt.pack.txt.launch_bat.render(opts, expandedClasspath)
    content.toString
  }

  def generateMakefile(PROG_NAME: String, PROG_SYMLINK: String): String = {
    val content = xerial.sbt.pack.txt.Makefile.render(PROG_NAME, PROG_SYMLINK)
    content.toString
  }*/

  private lazy val teng = new TemplateEngine()
  private var cachedTemplates: Map[URI, Template] = Map[URI, Template]()

  def generateScript(scriptSource: ScriptSource, opts: Opts): String = {
    val uri = scriptSource.uri
    val templ = if (!cachedTemplates.contains(uri)) {
      val tsrc = TemplateSource.fromSource(uri.toString, scriptSource.source)
      val template = teng.compile(tsrc)
      cachedTemplates += uri -> template
      template
    } else cachedTemplates(uri)
    teng.layout(uri.toString, templ, Map("opts" -> opts))
  }

  def generateMakefile(scriptSource: ScriptSource, opts: MakefileOpts): String = {
    val uri = scriptSource.uri
    val templ = if (!cachedTemplates.contains(uri)) {
      val tsrc = TemplateSource.fromSource(uri.toString, scriptSource.source)
      val template = teng.compile(tsrc)
      cachedTemplates += uri -> template
      template
    } else cachedTemplates(uri)
    teng.layout(uri.toString, templ, Map("opts" -> opts))
  }

  case class Opts(
    mainClass: String,
    progName: String,
    progVersion: String,
    progRevision: String,
    extraClasspath: Seq[String],
    expandedClasspath: Seq[String],
    jvmOpts: Seq[String] = Nil,
    macIconFile: String = "icon-mac.png"
  )

  case class MakefileOpts(
    projectName: String,
    symLinks: Seq[String]
  )

}
