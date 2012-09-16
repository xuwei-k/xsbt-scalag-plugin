package scalag

import sbt._
import sbt.Keys._
import sbt.complete.Parser
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

  val scalagTask = InputTask(_ => scalagParser) {
    task =>
      (task, scalaSource in Compile, scalaSource in Test,
        resourceDirectory in Compile, resourceDirectory in Test) map {
          case ((n, args), srcDir, testDir, resourceDir, testResourceDir) =>
            if (!isFrozen) {
              freeze()
            }
            val settings = SbtSettings(
              srcDir = srcDir,
              testDir = testDir,
              resourceDir = resourceDir,
              testResourceDir = testResourceDir
            )
            (operations.map { _.operation }.reduceLeft { _ orElse _ } orElse defaultOperation)(ScalagInput(n +: args.mkString.split(" ").toList, settings))
        }
  }

  private lazy val scalagParser = {
    val operationNames = operations.map { _.help.namespace }
    Space ~> operationNames.tail.foldLeft(token(operationNames.head)) { _ | _ } ~ ((Space ?) ~> (any *))
  }

  lazy val generate = InputKey[Unit]("generate")
  lazy val g = InputKey[Unit]("g")

  val scalagSettings = inConfig(Compile)(Seq(
    g <<= scalagTask,
    generate <<= scalagTask
  ))

  private[this] val defaultOperation: ScalagOperation = {
    case ScalagInput(Nil, _) => showHelp()
  }

  /**
   * Scalag command operations
   */
  private[this] var operations: Seq[ScalagCommand] = Nil

  private def reducedOp = operations.map { _.operation }.reduceLeft { _ orElse _ }

  /**
   * Is already frozen?
   */
  private[this] var isFrozen = false

  /**
   * Scalag help
   */
  private[this] val helps = new StringBuilder() ++= "Usage: g [task-name] [options...] \n\n"

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
    Option(command).foreach { command =>
      this.synchronized {
        if (isFrozen) {
          throw new ScalagStateException("Scalag is already frozen. You cannnot add commands any more.")
        } else {
          if (command.help.namespace != "") {
            helps ++= command.help.toString
          }
          operations :+= command
        }
      }
    }
  }

  /**
   * Freezes Scalag
   */
  def freeze(): Unit = {
    this.synchronized {
      operations
      /* TODO
      {
        case _ => showHelp()
      }
      */
      isFrozen = true
    }
  }

  /**
   * Shows help
   */
  def showHelp(): Unit = println(helps.toString)

}

