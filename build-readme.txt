==============================================================================
Building this Project
Matt Tropiano's Doom Utilities
(C) 2013-2014
http://mtrop.net
==============================================================================

This project is built via Apache Ant. You'll need to download Ant from here:
https://ant.apache.org/bindownload.cgi

The build script (build.xml) contains multiple targets of note, including:

clean 
	Cleans the build directory contents.
dependencies
	Pulls dependencies into the dependency folders and adds them to 
	build.properties for "dev.base".
compile
	Compiles the Java source to classes.
project.NAME
	Builds the project called NAME for release.
release
	Builds every project for release.

The build script also contains multiple properties of note, including:

build.version.number
	Version number of the build.
	Default: Current time formatted as "yyyy.MM.dd.HHmmssSSS".
build.version.appendix
	Type of build (usually "BUILD" or "RELEASE" or "STABLE" or "SNAPSHOT").
	Default: "SNAPSHOT".
dev.base
	The base directory for other projects that need including for the build.
	Default: ".."
build.dir
	The base directory for built resources.
	Default: "build"
launch4j.lib.dir
	The base directory for Launch4J (for Win32 EXEs).
	Default: "${dev.base}/lib/launch4j"
common.lib
	The location of the Black Rook Commons Library binaries (for build 
	classpath).
	Default: "${dev.base}/Common/bin"
common.io.lib
	The location of the Black Rook Common I/O binaries (for build 
	classpath).
	Default: "${dev.base}/CommonIO/bin"
common.lang.lib
	The location of the Black Rook Common Lang binaries (for build 
	classpath).
	Default: "${dev.base}/CommonLang/bin"
doom.lib
	The location of the Black Rook Doom Struct binaries (for build 
	classpath).
	Default: "${dev.base}/Doom/bin"
utility.lib
	The location of the Black Rook Utility binaries (for build 
	classpath).
	Default: "${dev.base}/Utility/bin"
