name := "payment-service"
version := "1.0.0"
scalaVersion := "3.4.3"

lazy val zioVersion = "2.0.19"
lazy val zioHttpVersion = "3.0.0-RC3"
lazy val zioJsonVersion = "0.6.2"

libraryDependencies ++= Seq(
  // ZIO Core
  "dev.zio" %% "zio" % zioVersion,
  "dev.zio" %% "zio-streams" % zioVersion,
  "dev.zio" %% "zio-concurrent" % zioVersion,

  // ZIO HTTP (for API endpoints)
  "dev.zio" %% "zio-http" % zioHttpVersion,

  // ZIO JSON
  "dev.zio" %% "zio-json" % zioJsonVersion,

  // Database - Quill with ZIO
  "io.getquill" %% "quill-jdbc-zio" % "4.8.0",
  "org.postgresql" % "postgresql" % "42.7.0",
  "com.zaxxer" % "HikariCP" % "5.1.0",

  // Logging
  "dev.zio" %% "zio-logging" % "2.1.14",
  "dev.zio" %% "zio-logging-slf4j" % "2.1.14",
  "ch.qos.logback" % "logback-classic" % "1.4.11",

  // Testing
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "dev.zio" %% "zio-test-magnolia" % zioVersion % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

// Main class configuration
Compile / mainClass := Some("com.fintech.payment.PaymentApp")
run / mainClass := Some("com.fintech.payment.PaymentApp")

// Scala compiler options
scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Xfatal-warnings"
)

