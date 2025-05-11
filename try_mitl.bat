javac -cp .;geo.jar mitl/*.java
jar -cf mitl.jar -C . mitl
java -cp geo.jar;mitl.jar mitl.Mapout 49.479964 10.988507 4096 2048 400.0 mapout.png
start "" "mapout.png"