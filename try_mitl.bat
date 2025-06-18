javac -cp .;geo.jar mitl/*.java
jar -cf mitl.jar -C . mitl
java -cp geo.jar;mitl.jar mitl.Mapout 49.480061 10.988551 10000 5000 70000 Masstab1000_StMichael_Fuerth.png
start "" "mapout.png"