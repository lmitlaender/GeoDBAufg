javac -cp .;geo.jar mitl/*.java
jar -cf mitl.jar -C . mitl
java -cp geo.jar;mitl.jar mitl.Mapout 49.445555 11.082587 1024 512 1234.5 mapout.png
start "" "mapout.png"