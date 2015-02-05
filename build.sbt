name := "google-ddns"

version := "1.0"

scalaVersion := "2.11.4"

exportJars := true

resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"

assemblyJarName in assembly := "google-ddns.jar"

libraryDependencies ++= Seq(
    "org.scalaj" %% "scalaj-http" % "1.1.1"
    ,"com.typesafe.play" %% "play-json" % "2.3.0"
    ,"commons-codec" % "commons-codec" % "1.9"
    ,"log4j" % "log4j" % "1.2.14"
    //,"com.typesafe.akka" %% "akka-actor" % "2.3.9"
)
