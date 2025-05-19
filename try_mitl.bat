javac -cp .;geo.jar mitl/*.java
jar -cf mitl.jar -C . mitl
java -cp geo.jar;mitl.jar mitl.Mapout 49.469925 10.990074 2048 1024 500.5 mapout.png
start "" "mapout.png"