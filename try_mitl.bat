javac -cp .;geo.jar mitl/*.java
jar -cf mitl.jar -C . mitl
java -cp geo.jar;mitl.jar mitl.Mapout 49.480047 10.988539 2048 1024 1000 mapout.png
start "" "mapout.png"