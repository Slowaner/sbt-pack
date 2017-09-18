package xerial.sbt.pack

import java.io.InputStream

import scala.io.{BufferedSource, Codec, Source}

import sbt.{URI, URL}

trait ScriptSource {
  val source: Source
  val uri: URI
}

object ScriptSource {
  def apply(templateSource: Source, templateUri: URI): ScriptSource = new ScriptSource() {
    override val source: Source = templateSource
    override val uri: URI = templateUri
  }


  def apply(templateUri: URI, codec: Codec): ScriptSource = new ScriptSource() {
    override val source: BufferedSource = Source.fromURI(templateUri)(codec)
    override val uri: URI = templateUri
  }

  def apply(templateUri: URI): ScriptSource = apply(templateUri, Codec.UTF8)

  def apply(templateUrl: URL, codec: Codec): ScriptSource = new ScriptSource() {
    override val source: BufferedSource = Source.fromURL(templateUrl)(codec)
    override val uri: URI = templateUrl.toURI
  }

  def apply(templateUrl: URL): ScriptSource = apply(templateUrl, Codec.UTF8)

  def apply(templateUri: URI, templateInputStream: InputStream, codec: Codec = Codec.UTF8): ScriptSource = new ScriptSource() {
    override val source: BufferedSource = Source.fromInputStream(templateInputStream)(codec)
    override val uri: URI = templateUri
  }

  def apply(templateUri: URI, templateInputStream: InputStream): ScriptSource = apply(templateUri, templateInputStream, Codec.UTF8)

  def apply(templateString: String, templateUri: URI): ScriptSource = new ScriptSource() {
    override val source: Source = Source.fromString(templateString)
    override val uri: URI = templateUri
  }
}