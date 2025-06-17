javac -cp .;geo.jar mitl/*.java
jar -cf mitl.jar -C . mitl
java -cp geo.jar;mitl.jar mitl.Mapout 49.454278 11.103444 2048 1024 2000 mapout.png
start "" "mapout.png"