javac -cp .;geo.jar mitl/*.java
jar -cf mitl.jar -C . mitl
java -cp geo.jar;mitl.jar mitl.Mapout 49.476344 10.988873 4096 2048 500 mapout.png
start "" "mapout.png"