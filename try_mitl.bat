javac -cp .;geo.jar mitl/*.java
jar -cf mitl.jar -C . mitl
java -cp geo.jar;mitl.jar mitl.Mapout 49.480020 10.998567 4096 2048 2000.0 mapout.png
start "" "mapout.png"