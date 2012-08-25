#! /bin/sh
javac -d classes -cp src src/*.java src/*/*.java
g++ -o classes/screenxfork src/screenxfork.cc
