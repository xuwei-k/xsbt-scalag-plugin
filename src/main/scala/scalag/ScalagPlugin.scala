package scalag

import sbt._
import sbt.Keys._

import sbt.complete.DefaultParsers._

/**
 * Scalag xsbt plugin
 *
 * {{{
 * import sbt._
 * import sbt.Keys._
 * import scalag._
 *
 * object MyScalagDef extends Plugin {
 *   ScalagPlugin.addCommand(builtin.classCommand)
 * }
 * }}}
 */
object ScalagPlugin extends Plugin {

  /**
   * For tab completion
   */
  private[this] lazy val scalagParser = {
    val namespaces: Seq[String] = commands.map(_.namespace)
    (Space ?) ~> (namespaces.tail.foldLeft(token(namespaces.head))(_ | _) ?) ~ ((Space ?) ~> (any *))
  }

  /**
   * Scalag task
   */
  val scalagTask = InputTask(_ => scalagParser) {
    (task: TaskKey[(Option[String], Seq[Char])]) =>
      (task, scalaSource in Compile, scalaSource in Test,
        resourceDirectory in Compile, resourceDirectory in Test) map {
          case ((namespace, chars), srcDir, testDir, resourceDir, testResourceDir) =>
            val settings = SbtSettings(
              srcDir = srcDir,
              testDir = testDir,
              resourceDir = resourceDir,
              testResourceDir = testResourceDir
            )
            namespace.map {
              case ns =>
                val args: List[String] = chars.mkString.split("\\s+")
                  .filter(a => a != null && a.trim.size > 0)
                  .map(a => a.trim()).toList
                operation.apply(ScalagInput(ns :: args, settings))
            }.getOrElse {
              operation.apply(ScalagInput(Nil, settings))
            }
        }
  }

  /**
   * Scalag InputKeys
   */
  lazy val g = InputKey[Unit]("g")
  lazy val generate = InputKey[Unit]("generate")

  /**
   * xsbt configuration
   */
  val scalagSettings = inConfig(Compile)(Seq(
    g <<= scalagTask,
    generate <<= scalagTask
  ))

  /**
   * Scalag command operations
   */
  private[this] var commands: Seq[ScalagCommand] = Nil

  /**
   * Default operation
   */
  private[this] val defaultOperation: ScalagOperation = {
    case ScalagInput(Nil, _) => showHelp()
  }

  /**
   * Scalag operation
   * @return operation
   */
  private[this] def operation: ScalagOperation = {
    val allOps = commands.foldLeft(defaultOperation) {
      case (ops, cmd) =>
        val op: ScalagOperation = {
          case ScalagInput(cmd.namespace :: args, settings) =>
            val showCommandHelp: ScalagOperation = {
              case ScalagInput(_, _) => cmd.help.showUsage()
            }
            cmd.operation.orElse(showCommandHelp).apply(ScalagInput(args, settings))
        }
        ops orElse op
    }
    val showHelpToAll: ScalagOperation = {
      case ScalagInput("--help" :: _, _) => showHelp()
      case ScalagInput("--h" :: _, _) => showHelp()
    }
    allOps orElse showHelpToAll
  }

  /**
   * Scalag help
   */
  private[this] def help: String = {
    "Usage: g [task-name] [args...] \n\n" + commands.map(cmd => cmd.help.toString).mkString("")
  }

  /**
   * Add new commands to Scalag
   * @param commands commands
   */
  def addCommands(commands: ScalagCommand*): Unit = {
    commands.foreach(cmd => addCommand(cmd))
  }

  /**
   * Add new command to Scalag
   * @param command scalag command
   */
  def addCommand(command: ScalagCommand): Unit = {
    Option(command).foreach(command =>
      addCommand(
        operation = command.operation,
        namespace = command.namespace,
        args = command.help.args,
        description = command.help.description
      )
    )
  }

  /**
   * Add new command to Scalag
   * @param operation scalag command operation
   * @param namespace scalag help namespace
   * @param description scalag help description
   */
  def addCommand(operation: ScalagOperation, namespace: String = "", args: Seq[String] = Nil, description: String = ""): Unit = {
    this.synchronized {
      commands :+= ScalagCommand(
        namespace = namespace,
        help = ScalagHelp(namespace, args, description),
        operation = operation
      )
    }
  }

  /**
   * Shows help
   */
  def showHelp(): Unit = println(help)

}

