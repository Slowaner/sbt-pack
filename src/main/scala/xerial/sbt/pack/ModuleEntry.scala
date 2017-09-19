package xerial.sbt.pack

import sbt.File

case class ModuleEntry(org: String,
  name: String,
  revision: VersionString,
  artifactName: String,
  classifier: Option[String],
  file: File) {
  private def classifierSuffix = classifier.map("-" + _).getOrElse("")

  override def toString: String = "%s:%s:%s%s".format(org, artifactName, revision, classifierSuffix)

  def originalFileName: String = file.getName

  def jarName: String = "%s-%s%s.jar".format(artifactName, revision, classifierSuffix)

  def fullJarName: String = "%s.%s-%s%s.jar".format(org, artifactName, revision, classifierSuffix)

  def noVersionJarName: String = "%s.%s%s.jar".format(org, artifactName, classifierSuffix)

  def noVersionModuleName: String = "%s.%s%s.jar".format(org, name, classifierSuffix)

  def toDependencyStr: String = s""""$org" % "$name" % "$revision""""
}
