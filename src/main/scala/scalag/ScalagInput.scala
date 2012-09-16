package scalag

/**
 * Scalag command input
 *
 * @param args args from sbt
 * @param settings sbt setting values
 */
case class ScalagInput(args: List[String], settings: SbtSettings)

