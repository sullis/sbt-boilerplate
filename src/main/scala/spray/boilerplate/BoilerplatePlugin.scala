/*
 * sbt-boilerplate is distributed under the 2-Clause BSD license. See the LICENSE file in the root
 * of the repository.
 *
 * Copyright (c) 2012 Johannes Rudolph
 */
package spray.boilerplate

import sbt._
import Keys._

object BoilerplatePlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def `requires`: Plugins = empty

  object autoImport {
    val boilerplateGenerate = taskKey[Seq[File]]("Generates boilerplate from template files")
    val boilerplateSource = settingKey[File]("Default directory containing boilerplate template sources.")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] =
    inConfig(Compile)(rawBoilerplateSettings) ++ inConfig(Test)(rawBoilerplateSettings)

  private def rawBoilerplateSettings: Seq[Setting[_]] = {
    val inputFilter = "*.template"
    Seq(
      boilerplateSource := sourceDirectory.value / "boilerplate",
      watchSources in Defaults.ConfigGlobal ++= ((boilerplateSource.value ** inputFilter) --- (boilerplateSource.value ** excludeFilter.value ** inputFilter)).get,
      boilerplateGenerate := generateFromTemplates(streams.value, boilerplateSource.value, sourceManaged.value),
      mappings in packageSrc ++= managedSources.value pair (Path.relativeTo(sourceManaged.value) | Path.flat),
      sourceGenerators <+= boilerplateGenerate)
  }

  def generateFromTemplates(streams: TaskStreams, sourceDir: File, targetDir: File): Seq[File] = {
    val files = sourceDir ** "*.template"

    def changeExtension(f: File): File = {
      val (_, name) = f.getName.reverse.span(_ != '.')
      val strippedName = name.drop(1).reverse.toString
      val newName =
        if (!strippedName.contains(".")) s"$strippedName.scala"
        else strippedName
      new File(f.getParent, newName)
    }

    val mapping = (files pair rebase(sourceDir, targetDir)).map {
      case (orig, target) ⇒ (orig, changeExtension(target))
    }

    mapping foreach {
      case (templateFile, target) ⇒
        if (templateFile.lastModified > target.lastModified) {
          streams.log.info("Generating '%s'" format target.getName)
          val template = IO.read(templateFile)
          IO.write(target, Generator.generateFromTemplate(template, 22))
        } else
          streams.log.debug("Template '%s' older than target. Ignoring." format templateFile.getName)
    }

    mapping.map(_._2)
  }
}
