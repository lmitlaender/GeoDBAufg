

import java.util.ArrayList;

import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.Point;

import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;

import fu.keys.LSIClassCentreDB;
import fu.util.DBUtil;
import fu.util.DoubleMetaphone;
import fu.util.ConcaveHullGenerator;



import pp.dorenda.client2.additional.UniversalPainterWriter;



public class DBDemo {

    private static GeometryFactory geomfact=new GeometryFactory();


    public static void main(String args[]) {
        Connection connection=null;  


        long time;
        int cnt;
        ResultSet resultSet;
        Statement statement;


        if (args.length!=1) {
            System.out.println("DB Access String as single param expected");
            System.exit(1);
        }


        try {
            /* Zugang zur Datenbank einrichten */
            DBUtil.parseDBparams(args[0],0);
            connection=DBUtil.getConnection(0);
            connection.setAutoCommit(false);  //Getting results based on a cursor
            LSIClassCentreDB.initFromDB(connection);
        }
        catch (Exception e) {
            System.out.println("Error initialising DB access: "+e.toString());
            e.printStackTrace();
            System.exit(1);
        }

        try {


// ************* DEMO-ABFRAGE 1: Domain-Tabelle (alle Objekt in einem Dreieck) *************

            // KONSTRUKTION EINER VERGLEICHSGEOMETRIE

            Coordinate[] coords=new Coordinate[4];
            coords[0]=new Coordinate(11.097026,49.460811);
            coords[1]=new Coordinate(11.104676,49.460811);
            coords[2]=new Coordinate(11.101730,49.455367);
            coords[3]=coords[0];
            Geometry triangle=geomfact.createPolygon(geomfact.createLinearRing(coords),new LinearRing[0]);


            System.out.println("Abfrage: Alle Objekte Strassen im Bereich des angegebenen Dreiecks (Naehe Informatik-Gebaeude)");
            
            int[] lcStrassen=LSIClassCentreDB.lsiClassRange("STRASSEN_WEGE");
            
            time=System.currentTimeMillis(); // Zeitmessung beginnen

            PreparedStatement pstatement=connection.prepareStatement("SELECT realname, ST_AsEWKB(geom :: geometry) FROM domain WHERE (geometry='L' OR geometry='C') AND lsiclass1 BETWEEN ? AND ? AND ST_within(geom :: geometry,ST_GeomFromWKB(?,4326)) ORDER BY ST_Length(geom) DESC");

            int col=1;
            pstatement.setInt(col++,lcStrassen[0]);
            pstatement.setInt(col++,lcStrassen[1]);
            pstatement.setBytes(col++,new WKBWriter().write(triangle));

            pstatement.setFetchSize(1000);

            resultSet = pstatement.executeQuery();

            cnt=0;
 
            while (resultSet.next()) {
                col=1;
                String realname=resultSet.getString(col++);
                byte[] geomdata=resultSet.getBytes(col++);
                Geometry geom=new WKBReader().read(geomdata);
                System.out.println(realname);
                dumpGeometry(geom);
                cnt++;
            }
            resultSet.close();
            System.out.println("Anzahl der Resultate: "+cnt);
            System.out.println("Zeit "+(System.currentTimeMillis()-time)/1000+" s");
            System.out.println("Ende Abfrage");
            System.out.println("=====================================================");


// ************* DEMO-ABFRAGE 2: CROSSING-TABELLE *************
// Alle Kreuzungen innerhalb eines Rechtecks
// Das Kreuzungen keinen raeumlichen Index haben, kann die Rechteck-Bedingung direkt als SQL formuliert werden

            System.out.println("Abfrage: Topologie-Kreuzungspunkte im Rechteck");
            time=System.currentTimeMillis(); // Zeitmessung beginnen

            statement=connection.createStatement();
            statement.setFetchSize(1000);
            resultSet = statement.executeQuery("SELECT id,partnr,ST_X(location :: geometry),ST_Y(location :: geometry) FROM crossing WHERE ST_within(location :: geometry,ST_MakeEnvelope(11.10069333,49.45725500,11.10392667,49.45987833,4326)) ORDER BY id,partnr");

            cnt=0;
 
            while (resultSet.next()) {
                col=1;
                int db_id=(int)resultSet.getLong(col++);
                int db_partnr=(int)resultSet.getLong(col++);
                double db_long=resultSet.getDouble(col++);
                double db_lat=resultSet.getDouble(col++);
                System.out.println(""+db_id+" "+db_partnr+" "+db_lat+" "+db_long);
                cnt++;
            }
            resultSet.close();
            System.out.println("Anzahl der Resultate: "+cnt);
            System.out.println("Zeit "+(System.currentTimeMillis()-time)/1000+" s");
            System.out.println("Ende Abfrage");
            System.out.println("=====================================================");

// ************* DEMO-ABFRAGE 3: LINK-TABELLE *************
// Streckenabschnitte, deren "Start" innerhalb eines Rechtecks liegen
// Das Streckenabschnitte keinen raeumlichen Index haben, kann die Rechteck-Bedingung direkt als SQL formuliert werden

            System.out.println("Abfrage: Topologie-Kanten im Rechteck");
            time=System.currentTimeMillis(); // Zeitmessung beginnen

            statement=connection.createStatement();
            statement.setFetchSize(1000);
            resultSet = statement.executeQuery("SELECT id,crossing_id_from,crossing_id_to,meters,lsiclass,tag,maxspeed,deadend,ST_X(location_from :: geometry),ST_Y(location_from :: geometry) FROM link WHERE ST_within(location_from :: geometry,ST_MakeEnvelope(11.10069333,49.45725500,11.10392667,49.45987833,4326)) ORDER BY id");

            cnt=0;
 
            while (resultSet.next()) {
                col=1;
                int db_id=(int)resultSet.getLong(col++);
                int db_crossing_id_from=(int)resultSet.getLong(col++);
                int db_crossing_id_to=(int)resultSet.getLong(col++);
                int db_meters=(int)resultSet.getLong(col++);
                int db_lsiclass=(int)resultSet.getLong(col++);
                String db_tag=resultSet.getString(col++);
                int db_maxspeed=(int)resultSet.getLong(col++);
                String db_deadend=resultSet.getString(col++);


                String lsikeyStr="#"+db_lsiclass;

                try {                
                    lsikeyStr=LSIClassCentreDB.className(db_lsiclass);
                }
                catch (Exception e) {}  // LSIClass not found

                System.out.print(""+db_id+", ");
                System.out.print(db_meters+"m, ");
                if (db_maxspeed>0)
                    System.out.print("max. "+db_maxspeed+"km/h, ");
                if (db_tag.equals("C"))
                    System.out.print("oneway, ");
                if (db_deadend.equals("D"))
                    System.out.print("deadend, ");


                System.out.println(lsikeyStr);
                cnt++;
            }
            resultSet.close();
            System.out.println("Anzahl der Resultate: "+cnt);
            System.out.println("Zeit "+(System.currentTimeMillis()-time)/1000+" s");
            System.out.println("Ende Abfrage");
            System.out.println("=====================================================");



// ************* DEMO-ABFRAGE 4: LINK-TABELLE *************
// Wie ABFRAGE 4 mit zwei Erweiterungen
// 1. Es werden nur "befahrbare Strassen" geladen
// 2. Ueber die Domain-Tabelle wird per Join der Strassenname geladen

            System.out.println("Abfrage: Topologie-Kanten im Rechteck");
            time=System.currentTimeMillis(); // Zeitmessung beginnen

            int[] lcStrassenBefahrbar=LSIClassCentreDB.lsiClassRange("KRAFTFAHRZEUGSTRASSEN");

            statement=connection.createStatement();
            statement.setFetchSize(1000);
            resultSet = statement.executeQuery("SELECT L.id,L.crossing_id_from,L.crossing_id_to,L.meters,L.lsiclass,L.tag,L.maxspeed,L.deadend,D.realname,D.nametype,ST_X(L.location_from :: geometry),ST_Y(L.location_from :: geometry) FROM link L, domain D WHERE ST_within(location_from :: geometry,ST_MakeEnvelope(11.10069333,49.45725500,11.10392667,49.45987833,4326)) AND lsiclass BETWEEN "+lcStrassenBefahrbar[0]+" AND "+lcStrassenBefahrbar[1]+" AND L.d_id=D.d_id ORDER BY id");

            cnt=0;
 
            while (resultSet.next()) {
                col=1;
                int db_id=(int)resultSet.getLong(col++);
                int db_crossing_id_from=(int)resultSet.getLong(col++);
                int db_crossing_id_to=(int)resultSet.getLong(col++);
                int db_meters=(int)resultSet.getLong(col++);
                int db_lsiclass=(int)resultSet.getLong(col++);
                String db_tag=resultSet.getString(col++);
                int db_maxspeed=(int)resultSet.getLong(col++);
                String db_deadend=resultSet.getString(col++);
                String db_realname=resultSet.getString(col++);
                int db_nametype=(int)resultSet.getLong(col++);

                String lsikeyStr="#"+db_lsiclass;

                try {                
                    lsikeyStr=LSIClassCentreDB.className(db_lsiclass);
                }
                catch (Exception e) {}  // LSIClass not found

                System.out.print(""+db_id+", ");
                System.out.print(db_meters+"m, ");
                if (db_maxspeed>0)
                    System.out.print("max. "+db_maxspeed+"km/h, ");
                if (db_tag.equals("C"))
                    System.out.print("oneway, ");
                if (db_deadend.equals("D"))
                    System.out.print("deadend, ");

                if (db_nametype<100)   // Nur nametype<100 sind echte Namen, sonst kuenstlich vergebene
                    System.out.print(db_realname+", ");

                System.out.println(lsikeyStr);
                cnt++;
            }
            resultSet.close();
            System.out.println("Anzahl der Resultate: "+cnt);
            System.out.println("Zeit "+(System.currentTimeMillis()-time)/1000+" s");
            System.out.println("Ende Abfrage");
            System.out.println("=====================================================");



// ************* DEMO-ABFRAGE 5: Domain-Tabelle nach aehnlichen Objektnamen fragen  *************
// Hiermit wird der Double-Metaphone getestet

            String searchString="Burgberg";  // Alle Domains fragen, die aehnlich klingen

            DoubleMetaphone dm=new DoubleMetaphone();
            dm.setMaxCodeLen(6);
            String dmPrimary=dm.doubleMetaphone(searchString,false);
            String dmAlternate=dm.doubleMetaphone(searchString,true);


            System.out.println("Abfrage: Domains in einem Rechteck, die so aehnlich heissen wie '"+searchString);
            time=System.currentTimeMillis(); // Zeitmessung beginnen

            statement=connection.createStatement();
            statement.setFetchSize(1000);
            resultSet = statement.executeQuery("SELECT realname FROM domain WHERE (dmetaphone_primary='"+dmPrimary+"' OR dmetaphone_alternate='"+dmAlternate+"') AND "+
                                               "ST_within(geom :: geometry,ST_MakeEnvelope(11.0,49.8,11.2,49.2,4326))"
                                              );

            cnt=0;
 
            while (resultSet.next()) {
                String realname=resultSet.getString(1);
                System.out.println(realname);
                cnt++;
            }
            resultSet.close();
            System.out.println("Anzahl der Resultate: "+cnt);
            System.out.println("Zeit "+(System.currentTimeMillis()-time)/1000+" s");
            System.out.println("Ende Abfrage");
            System.out.println("=====================================================");



// ************* DEMO-ABFRAGE 6: Objekt fuer dorenda malen *************
// Eine Objektgeomtrie wird aus der Datenbank geholt und fuer dorenda gemalt

            System.out.println("Abfrage: Domain laden und fuer dorenda malen");
            time=System.currentTimeMillis(); // Zeitmessung beginnen

            statement=connection.createStatement();
            statement.setFetchSize(1000);
            resultSet = statement.executeQuery("select realname,ST_AsEWKB(geom :: geometry) from domain where realname ='Lorenzer Platz' AND geometry='A'");

            cnt=0;
 
            UniversalPainterWriter upw=new UniversalPainterWriter("paint.txt");


            while (resultSet.next()) {
                col=1;
                String realname=resultSet.getString(col++);
                byte[] geomdata=resultSet.getBytes(col++);
                Geometry geom=new WKBReader().read(geomdata);
                System.out.println(realname);
                dumpGeometry(geom);

                upw.jtsGeometry(geom,255,255,255,100,4,4,4);

                cnt++;
            }
            resultSet.close();
            upw.close();

            System.out.println("Anzahl der Resultate: "+cnt);
            System.out.println("Zeit "+(System.currentTimeMillis()-time)/1000+" s");
            System.out.println("Ende Abfrage");
            System.out.println("=====================================================");

        }
        catch (Exception e) {
            System.out.println("Error processing DB queries: "+e.toString());
            e.printStackTrace();
            System.exit(1);
        }   
    }



    public static void dumpGeometry(Geometry geom) {
        if (geom instanceof Polygon) {
            System.out.println("Class: Polygon");
            LineString extring=((Polygon)geom).getExteriorRing();
            System.out.println(extring.getNumPoints()+" exterior ring points");
            int n=3;
            if (n>=extring.getNumPoints())
                n=extring.getNumPoints();
            System.out.println("First "+n+" poly points:");
            for (int i=0;i<n;i++) {
                 Coordinate coord=extring.getCoordinateN(i);
                 dumpCoord(coord);
            }
            System.out.println("");
        }

        else if (geom instanceof MultiPolygon) {
            System.out.println("Class: MultiPolygon");
            System.out.println(geom.getNumGeometries()+" part geometries");
            Geometry firstgeom=geom.getGeometryN(0);
            LineString extring=((Polygon)firstgeom).getExteriorRing();
            System.out.println(extring.getNumPoints()+" exterior ring points (geometry 0)");
            int n=3;
            if (n>=extring.getNumPoints())
                n=extring.getNumPoints();
            System.out.println("First "+n+" poly points (geometry 0):");
            for (int i=0;i<n;i++) {
                 Coordinate coord=extring.getCoordinateN(i);
                 dumpCoord(coord);
            }
            System.out.println("");

        }

        else if (geom instanceof LineString) {
            System.out.println("Class: LineString");
            LineString listring=((LineString)geom);
            System.out.println(listring.getNumPoints()+" line points");
            int n=3;
            if (n>=listring.getNumPoints())
                n=listring.getNumPoints();
            System.out.println("First "+n+" line points:");
            for (int i=0;i<n;i++) {
                 Coordinate coord=listring.getCoordinateN(i);
                 dumpCoord(coord);
            }
            System.out.println("");
        }

        else if (geom instanceof MultiLineString) {
            System.out.println("Class: MultiLineString");
            System.out.println(geom.getNumGeometries()+" part geometries");
            Geometry firstgeom=geom.getGeometryN(0);
            LineString listring=((LineString)firstgeom);
            System.out.println(listring.getNumPoints()+" line points (geometry 0)");
            int n=3;
            if (n>=listring.getNumPoints())
                n=listring.getNumPoints();
            System.out.println("First "+n+" line points  (geometry 0):");
            for (int i=0;i<n;i++) {
                 Coordinate coord=listring.getCoordinateN(i);
                 dumpCoord(coord);
            }
            System.out.println("");

        }

        else if (geom instanceof Point) {
            System.out.println("Class: Point");
            Coordinate coord=((Point)geom).getCoordinate();
            dumpCoord(coord);
        }
        else {
            System.out.println("don't know how to tell something about "+geom.getClass().getName());        
        }
    }


    public static void dumpCoord(Coordinate coord) {
        System.out.print(coord.y+","+coord.x);   // Lat,Long
        if (!Double.isNaN(coord.z))
            System.out.print(" ("+Math.round(coord.z)+"m)");
        System.out.print(" ");
    }

}