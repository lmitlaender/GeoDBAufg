javac -cp .;geo.jar mitl/*.java
jar -cf mitl.jar -C . mitl
java -cp geo.jar;mitl.jar mitl.Mapout 49.471591 10.984916 4096 2048 2000.0 mapout.png
start "" "mapout.png"