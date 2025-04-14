package mitl;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;
import com.vividsolutions.jts.io.WKTWriter;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

public class Mapout {

    private static GeometryFactory geomfact = new GeometryFactory();
    private static UTMProjection utmProjection = new UTMProjection();

    private static double meterPerPixel = 0;

    private static double offsetX = 0;
    private static double offsetY = 0;


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

        meterPerPixel = meter_x / x;


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
            Geometry queryGeometry = get_query_geometry(lat, lon, x, y, meter_x, connection);
            System.out.println(new WKTWriter().writeFormatted(queryGeometry));

            // Leeres Bild anlegen
            BufferedImage image = new BufferedImage(x, y, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = (Graphics2D) image.getGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Antialiasing einschalten

            PreparedStatement preparedStatement = connection.prepareStatement("""
                    SELECT realname, ST_AsEWKB(geom :: geometry)
                    FROM domain WHERE (geometry='L' OR geometry='C') AND ((lsiclass1 BETWEEN ? AND ?) OR (lsiclass1 BETWEEN ? AND ?)) AND ST_Intersects(geom :: geometry, ST_GeomFromWKB(?,4326))
                    ORDER BY ST_Length(geom) DESC
                    """
            );

            int[] lcStrassen=LSIClassCentreDB.lsiClassRange("STRASSEN_WEGE");
            int[] lcRail=LSIClassCentreDB.lsiClassRange("BAHNVERKEHR");

            int col=1;
            preparedStatement.setInt(col++,lcStrassen[0]);
            preparedStatement.setInt(col++,lcStrassen[1]);
            preparedStatement.setInt(col++,lcRail[0]);
            preparedStatement.setInt(col++,lcRail[1]);
            preparedStatement.setBytes(col++,new WKBWriter().write(queryGeometry));

            preparedStatement.setFetchSize(1000);

            resultSet = preparedStatement.executeQuery();

            cnt = 0;

            while (resultSet.next()) {
                col = 1;
                String realname = resultSet.getString(col++);
                byte[] geomdata = resultSet.getBytes(col++);
                Geometry geom = new WKBReader().read(geomdata);
                System.out.println(realname);

                paintGeometry(g, geom, realname, "fontain");

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

    public static Geometry get_query_geometry(double lat, double lon, int mapX, int mapY, double meterX, Connection connection) {
        Coordinate[] coords = new Coordinate[5];

        double halfMeterX = meterX / 2;
        double meterY = meterPerPixel * mapY;
        double halfMeterY = meterY / 2;

        System.out.println(halfMeterX + " " + halfMeterY);

        double[] utmCenter = utmProjection.project(lat, lon);
        int zone = utmProjection.getZone(lon);
        double[] invLeftTop = utmProjection.inverseProject(utmCenter[0] - halfMeterX, utmCenter[1] - halfMeterY, zone, connection);
        double[] invRightTop = utmProjection.inverseProject(utmCenter[0] + halfMeterX, utmCenter[1] - halfMeterY, zone, connection);
        double[] invRightBottom = utmProjection.inverseProject(utmCenter[0] + halfMeterX, utmCenter[1] + halfMeterY, zone, connection);
        double[] invLeftBottom = utmProjection.inverseProject(utmCenter[0] - halfMeterX, utmCenter[1] + halfMeterY, zone, connection);

        offsetX = utmCenter[0] - halfMeterX;
        offsetY = utmCenter[1] - halfMeterY;

        coords[0] = new Coordinate(invLeftTop[1], invLeftTop[0]);
        coords[1] = new Coordinate(invRightTop[1], invRightTop[0]);
        coords[2] = new Coordinate(invRightBottom[1], invRightBottom[0]);
        coords[3] = new Coordinate(invLeftBottom[1], invLeftBottom[0]);
        coords[4] = coords[0];

        return geomfact.createPolygon(geomfact.createLinearRing(coords), new LinearRing[0]);
    }
    public static void paintGeometry(Graphics2D g,Geometry geom,String name,String icon) throws IOException {
        if (geom instanceof com.vividsolutions.jts.geom.Polygon) {

            /*LineString extring=((Polygon)geom).getExteriorRing();
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

            g.drawString(name,200,200);*/
            System.out.println("Drawing Poly");

        }
        else if (geom instanceof com.vividsolutions.jts.geom.LineString) {
            LineString ls = (LineString) geom;
            Coordinate[] coordinates = ls.getCoordinates();

            for (int i = 0; i < ls.getNumPoints() - 1; ++i) {
                double[] point1 = utmProjection.project(coordinates[i].y, coordinates[i].x);
                double[] point2 = utmProjection.project(coordinates[i + 1].y, coordinates[i + 1].x);
                point1[0] -= offsetX;
                point1[1] -= offsetY;
                point2[0] -= offsetX;
                point2[1] -= offsetY;

                g.setColor(Color.RED);
                g.drawLine((int) (point1[0] / meterPerPixel),2048 - (int) (point1[1] / meterPerPixel), (int) (point2[0] / meterPerPixel),2048 - (int) (point2[1] / meterPerPixel));
            }
        }
        else
            System.out.println("Don't know how to paint " + geom.getClass());
    }
}
