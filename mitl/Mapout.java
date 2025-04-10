package mitl;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKBReader;
import fu.geo.Spherical;
import fu.keys.LSIClassCentreDB;
import fu.util.DBUtil;
import mitl.projection.UTMProjection;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

public class Mapout {

    private static GeometryFactory geomfact = new GeometryFactory();
    private static UTMProjection utmProjection = new UTMProjection();

    public static void main(String[] args) {
        System.out.println("Mapout class is running");
        Connection connection=null;

        if (args.length != 6) {
            System.out.println("False number of args. Supply in order: Latitude, Longitude, x, y, meter_x, filename");
            System.exit(1);
        }

        double lat = Double.parseDouble(args[0]);
        double lon = Double.parseDouble(args[1]);
        int x = Integer.parseInt(args[2]);
        int y = Integer.parseInt(args[3]);
        double meter_x = Double.parseDouble(args[4]);
        String filename = args[5];

        utmProjection.project(lat, lon);

        get_query_geometry(lat, lon, x, y, meter_x);

        long time;
        int cnt;
        ResultSet resultSet;
        Statement statement;


        try {
            /* Zugang zur Datenbank einrichten */
            DBUtil.parseDBparams("127.0.0.1/5432/dbuser/dbuser/deproDBMittelfrankenPG",0);
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

            // Leeres Bild anlegen
            BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = (Graphics2D) image.getGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Antialiasing einschalten


// Eine Objektgeomtrie wird aus der Datenbank geholt und fuer dorenda gemalt

            statement=connection.createStatement();
            statement.setFetchSize(1000);
            resultSet = statement.executeQuery("select realname,ST_AsEWKB(geom :: geometry) from domain where realname ='Lorenzer Platz' AND geometry='A'");

            cnt=0;

            while (resultSet.next()) {
                int col=1;
                String realname=resultSet.getString(col++);
                byte[] geomdata=resultSet.getBytes(col++);
                Geometry geom=new WKBReader().read(geomdata);
                System.out.println(realname);

                paintGeometry(g,geom,realname,"fontain");

                cnt++;
            }
            resultSet.close();

            System.out.println("Schreibe Bild ...");
            g.dispose();
            ImageIO.write(image, "PNG", new File("demoimage_mitl.png"));  // Als die Karte als PNG speichern
            System.out.println("Bild geschrieben");

        }
        catch (Exception e) {
            System.out.println("Error processing DB queries: "+e.toString());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static Geometry get_query_geometry(double lat, double lon, int x, int y, double meter_x) {
        Coordinate[] coords = new Coordinate[5];

        double halfMeterX = meter_x / 2;
        double meterPerPixel = meter_x / x;
        double meterY = meterPerPixel * y;
        double halfMeterY = meterY / 2;

        System.out.println(halfMeterX + " " + halfMeterY);

        double[] cartesian_mid = Spherical.latLongToCartesian(lat, lon);
        double[] cartesian_mid_1 = Spherical.latLongToCartesian(lat, lon);
        cartesian_mid_1[0] = cartesian_mid[0] - 512;

        coords[0] = new Coordinate(Spherical.longitudeEastOf(lat, lon, -halfMeterY), Spherical.latitudeNorthOf(lat, lon, -halfMeterX));
        coords[1] = new Coordinate(Spherical.longitudeEastOf(lat, lon, halfMeterY), Spherical.latitudeNorthOf(lat, lon, -halfMeterX));
        coords[2] = new Coordinate(Spherical.longitudeEastOf(lat, lon, halfMeterY), Spherical.latitudeNorthOf(lat, lon, halfMeterX));
        coords[3] = new Coordinate(Spherical.longitudeEastOf(lat, lon, -halfMeterY), Spherical.latitudeNorthOf(lat, lon, halfMeterX));
        coords[4] = coords[0];

        coords[4] = coords[0];

        return geomfact.createPolygon(geomfact.createLinearRing(coords), new LinearRing[0]);
    }
    public static void paintGeometry(Graphics2D g,Geometry geom,String name,String icon) throws IOException {
        if (geom instanceof com.vividsolutions.jts.geom.Polygon) {

            LineString extring=((Polygon)geom).getExteriorRing();
            int n=extring.getNumPoints();

            double minX= 1e10;
            double minY= 1e10;
            double maxX=-1e10;
            double maxY=-1e10;

            for (int i=0;i<n;i++) {
                Coordinate coord=extring.getCoordinateN(i);
                minX=Math.min(minX,coord.x);
                minY=Math.min(minY,coord.y);
                maxX=Math.max(maxX,coord.x);
                maxY=Math.max(maxY,coord.y);
            }


            int[] x=new int[n];
            int[] y=new int[n];

            for (int i=0;i<n;i++) {
                Coordinate coord=extring.getCoordinateN(i);
                x[i]=(int)Math.round((coord.x-minX)*512/(maxX-minX));
                y[i]=511-(int)Math.round((coord.y-minY)*512/(maxY-minY));
            }

            g.setColor(Color.RED);
            g.drawPolygon(x,y,x.length);

            BufferedImage iconImage=ImageIO.read(new File("icons"+File.separator+icon+".png"));
            g.drawImage(iconImage,200,220,null);

            g.drawString(name,200,200);

        }
        else
            throw new IllegalArgumentException("Don't know how to paint "+geom.getClass());
    }
}
