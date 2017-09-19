#!/bin/sh

newVersion=`perl -npe "s/version in ThisBuild\s+:=\s+\"(.*)\"/\1/" version.sbt | sed -e "/^$/d"`

