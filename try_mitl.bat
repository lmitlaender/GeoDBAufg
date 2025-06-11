javac -cp .;geo.jar mitl/*.java
jar -cf mitl.jar -C . mitl
java -cp geo.jar;mitl.jar mitl.Mapout 49.480027 10.988543 1024 512 500 mapout.png
start "" "mapout.png"